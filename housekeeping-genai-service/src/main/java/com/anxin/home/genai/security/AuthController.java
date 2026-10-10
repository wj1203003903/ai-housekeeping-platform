package com.anxin.home.genai.security;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.http.ResponseCookie;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import jakarta.validation.constraints.Pattern;

@RestController
@RequestMapping("/auth")
public class AuthController {

    private final HousekeepingSecurityProperties properties;
    private final PlatformAccountRepository accountRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtEncoder jwtEncoder;
    private final RefreshTokenRepository refreshTokenRepository;
    private final SecureRandom secureRandom = new SecureRandom();
    private final String dummyPasswordHash;

    public AuthController(HousekeepingSecurityProperties properties,
                          PlatformAccountRepository accountRepository,
                          PasswordEncoder passwordEncoder,
                          JwtEncoder jwtEncoder,
                          RefreshTokenRepository refreshTokenRepository) {
        this.properties = properties;
        this.accountRepository = accountRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtEncoder = jwtEncoder;
        this.refreshTokenRepository = refreshTokenRepository;
        this.dummyPasswordHash = passwordEncoder.encode("timing-only-" + secureRandom.nextLong());
    }

    @PostMapping("/token")
    @Transactional(noRollbackFor = InvalidCredentialsException.class)
    TokenResponse token(@Valid @RequestBody LoginRequest request, ServerHttpResponse response) {
        preventCaching(response);
        PlatformAccount account = accountRepository.findByUsernameForUpdate(request.username()).orElse(null);
        if (account != null) {
            if (!account.isEnabled() || account.isLocked()) throw new InvalidCredentialsException();
            if (!passwordEncoder.matches(request.password(), account.getPasswordHash())) {
                account.recordFailedLogin();
                throw new InvalidCredentialsException();
            }
            account.recordSuccessfulLogin();
            return issueToken(account, true, response);
        }
        HousekeepingSecurityProperties.DemoUser user = properties.getUsers().get(request.username());
        if (user == null || !passwordEncoder.matches(request.password(), user.getPassword())) {
            if (user == null) passwordEncoder.matches(request.password(), dummyPasswordHash);
            throw new InvalidCredentialsException();
        }
        return issueToken(user.getUserId(), user.getRole(), 0);
    }

    @PostMapping("/refresh")
    @Transactional
    TokenResponse refresh(@RequestBody(required = false) RefreshRequest request,
                          @CookieValue(name = "HK_REFRESH", required = false) String cookieToken,
                          ServerHttpResponse response) {
        preventCaching(response);
        String rawToken = resolveRefreshToken(request, cookieToken);
        RefreshToken stored = refreshTokenRepository.findByTokenHashForUpdate(hash(rawToken))
            .filter(RefreshToken::isUsable).orElseThrow(InvalidCredentialsException::new);
        PlatformAccount account = accountRepository.findByUsername(stored.getAccountUsername())
            .filter(PlatformAccount::isEnabled).orElseThrow(InvalidCredentialsException::new);
        stored.revoke();
        return issueToken(account, true, response);
    }

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Transactional
    void logout(@RequestBody(required = false) RefreshRequest request,
                @CookieValue(name = "HK_REFRESH", required = false) String cookieToken,
                ServerHttpResponse response) {
        String rawToken = optionalRefreshToken(request, cookieToken);
        if (rawToken != null) {
            refreshTokenRepository.findByTokenHashForUpdate(hash(rawToken)).ifPresent(RefreshToken::revoke);
        }
        clearRefreshCookie(response);
    }

    @PostMapping("/change-password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Transactional
    void changePassword(@Valid @RequestBody ChangePasswordRequest request, @AuthenticationPrincipal Jwt jwt) {
        PlatformAccount account = accountRepository.findByUserIdAndRole(jwt.getSubject(), jwt.getClaimAsString("role"))
            .orElseThrow(InvalidCredentialsException::new);
        if (!passwordEncoder.matches(request.currentPassword(), account.getPasswordHash())) {
            throw new InvalidCredentialsException();
        }
        account.changePassword(passwordEncoder.encode(request.newPassword()));
        refreshTokenRepository.findByAccountUsername(account.getUsername()).forEach(RefreshToken::revoke);
    }

    private TokenResponse issueToken(PlatformAccount account, boolean withRefreshToken, ServerHttpResponse response) {
        if (withRefreshToken) setRefreshCookie(response, createRefreshToken(account.getUsername()));
        return issueToken(account.getUserId(), account.getRole(), account.getTokenVersion());
    }

    private TokenResponse issueToken(String userId, String role, int tokenVersion) {
        Instant issuedAt = Instant.now();
        Instant expiresAt = issuedAt.plus(properties.getTokenTtl());
        JwtClaimsSet claims = JwtClaimsSet.builder()
            .id(java.util.UUID.randomUUID().toString())
            .issuer(properties.getIssuer())
            .audience(java.util.List.of("housekeeping-api"))
            .issuedAt(issuedAt)
            .expiresAt(expiresAt)
            .subject(userId)
            .claim("role", role.toUpperCase())
            .claim("ver", tokenVersion)
            .build();
        String accessToken = jwtEncoder.encode(JwtEncoderParameters.from(claims)).getTokenValue();
        return new TokenResponse(accessToken, "Bearer", expiresAt, userId, role.toUpperCase());
    }

    private String createRefreshToken(String username) {
        byte[] bytes = new byte[48];
        secureRandom.nextBytes(bytes);
        String raw = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        refreshTokenRepository.save(new RefreshToken(hash(raw), username, Instant.now().plusSeconds(30L * 24 * 3600)));
        return raw;
    }

    private String hash(String value) {
        try {
            return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(value.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }

    private String resolveRefreshToken(RefreshRequest request, String cookieToken) {
        String value = optionalRefreshToken(request, cookieToken);
        if (value == null) throw new InvalidCredentialsException();
        return value;
    }

    private String optionalRefreshToken(RefreshRequest request, String cookieToken) {
        if (cookieToken != null && !cookieToken.isBlank()) return cookieToken;
        return request == null || request.refreshToken() == null || request.refreshToken().isBlank()
            ? null : request.refreshToken();
    }

    private void setRefreshCookie(ServerHttpResponse response, String token) {
        response.addCookie(ResponseCookie.from("HK_REFRESH", token)
            .httpOnly(true).secure(properties.isSecureCookies()).sameSite("Strict")
            .path("/api/genai/auth").maxAge(java.time.Duration.ofDays(30)).build());
    }

    private void clearRefreshCookie(ServerHttpResponse response) {
        response.addCookie(ResponseCookie.from("HK_REFRESH", "")
            .httpOnly(true).secure(properties.isSecureCookies()).sameSite("Strict")
            .path("/api/genai/auth").maxAge(java.time.Duration.ZERO).build());
    }

    private void preventCaching(ServerHttpResponse response) {
        response.getHeaders().setCacheControl("no-store");
        response.getHeaders().setPragma("no-cache");
    }

    record LoginRequest(@NotBlank String username, @NotBlank String password) {
    }

    record RefreshRequest(String refreshToken) {}
    record ChangePasswordRequest(@NotBlank String currentPassword,
                                 @NotBlank @Size(min = 10, max = 72)
                                 @Pattern(regexp = "^(?=.*[A-Za-z])(?=.*\\d).+$") String newPassword) {}

    record TokenResponse(String accessToken, String tokenType, Instant expiresAt,
                         String userId, String role) {
    }

    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    static class InvalidCredentialsException extends RuntimeException {
    }
}
