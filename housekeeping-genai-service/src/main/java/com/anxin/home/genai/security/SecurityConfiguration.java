package com.anxin.home.genai.security;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtClaimValidator;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusReactiveJwtDecoder;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.web.server.SecurityWebFilterChain;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.List;

@Configuration
@EnableConfigurationProperties(HousekeepingSecurityProperties.class)
public class SecurityConfiguration {

    @Bean
    SecurityWebFilterChain securityWebFilterChain(ServerHttpSecurity http,
                                                   HousekeepingSecurityProperties properties) {
        http.csrf(ServerHttpSecurity.CsrfSpec::disable)
            .authorizeExchange(exchange -> exchange
                .pathMatchers("/auth/token", "/auth/refresh", "/auth/logout", "/auth/register/**", "/auth/registration-options",
                    "/actuator/health", "/actuator/info").permitAll()
                .pathMatchers("/chatclient", "/actions/**", "/memory/**").authenticated()
                .pathMatchers("/actuator/**").hasRole("ADMIN")
                .anyExchange().denyAll())
            .oauth2ResourceServer(resourceServer -> resourceServer.jwt(Customizer.withDefaults()));
        return http.build();
    }

    @Bean
    SecretKey jwtSecretKey(HousekeepingSecurityProperties properties) {
        byte[] key = requiredKey(properties.getSecret(), "HOUSEKEEPING_JWT_SECRET");
        return new SecretKeySpec(key, "HmacSHA256");
    }

    @Bean("internalJwtSecretKey")
    SecretKey internalJwtSecretKey(HousekeepingSecurityProperties properties) {
        if (properties.getInternalSecret() != null && properties.getInternalSecret().equals(properties.getSecret())) {
            throw new IllegalStateException("User and internal JWT secrets must be different");
        }
        byte[] key = requiredKey(properties.getInternalSecret(), "HOUSEKEEPING_INTERNAL_JWT_SECRET");
        return new SecretKeySpec(key, "HmacSHA256");
    }

    private byte[] requiredKey(String value, String name) {
        if (value == null || value.isBlank()) throw new IllegalStateException(name + " must be configured");
        byte[] key = value.getBytes(StandardCharsets.UTF_8);
        if (key.length < 32) {
            throw new IllegalStateException(name + " must contain at least 32 bytes");
        }
        return key;
    }

    @Bean
    ReactiveJwtDecoder reactiveJwtDecoder(
        @org.springframework.beans.factory.annotation.Qualifier("jwtSecretKey") SecretKey secretKey,
        HousekeepingSecurityProperties properties) {
        NimbusReactiveJwtDecoder decoder = NimbusReactiveJwtDecoder.withSecretKey(secretKey)
            .macAlgorithm(MacAlgorithm.HS256)
            .build();
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
            JwtValidators.createDefaultWithIssuer(properties.getIssuer()),
            new JwtClaimValidator<List<String>>("aud", audience -> audience != null
                && audience.contains("housekeeping-api"))));
        return decoder;
    }

    @Bean
    JwtEncoder jwtEncoder(@org.springframework.beans.factory.annotation.Qualifier("jwtSecretKey") SecretKey secretKey) {
        return NimbusJwtEncoder.withSecretKey(secretKey)
            .algorithm(MacAlgorithm.HS256)
            .build();
    }

    @Bean("internalJwtEncoder")
    JwtEncoder internalJwtEncoder(@org.springframework.beans.factory.annotation.Qualifier("internalJwtSecretKey") SecretKey secretKey) {
        return NimbusJwtEncoder.withSecretKey(secretKey)
            .algorithm(MacAlgorithm.HS256)
            .build();
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }
}
