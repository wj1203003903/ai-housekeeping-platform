package com.anxin.home.genai.security;

import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Component
public class InternalServiceTokenProvider {
    private final JwtEncoder jwtEncoder;
    private volatile CachedToken cached;

    public InternalServiceTokenProvider(
        @org.springframework.beans.factory.annotation.Qualifier("internalJwtEncoder") JwtEncoder jwtEncoder) {
        this.jwtEncoder = jwtEncoder;
    }

    public String bearerToken() {
        Instant now = Instant.now();
        CachedToken current = cached;
        if (current != null && current.refreshAfter().isAfter(now)) return current.value();
        synchronized (this) {
            current = cached;
            if (current != null && current.refreshAfter().isAfter(now)) return current.value();
            Instant expiresAt = now.plusSeconds(300);
            JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer("housekeeping-internal")
                .audience(java.util.List.of("housekeeping-business-api"))
                .subject("genai-service")
                .issuedAt(now)
                .expiresAt(expiresAt)
                .claim("role", "INTERNAL")
                .build();
            String value = jwtEncoder.encode(JwtEncoderParameters.from(claims)).getTokenValue();
            cached = new CachedToken(value, now.plusSeconds(240));
            return value;
        }
    }

    private record CachedToken(String value, Instant refreshAfter) {
    }
}
