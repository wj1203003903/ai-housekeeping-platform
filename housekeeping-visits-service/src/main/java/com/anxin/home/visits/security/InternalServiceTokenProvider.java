package com.anxin.home.visits.security;

import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Component
public class InternalServiceTokenProvider {
    private final JwtEncoder encoder;
    private volatile String token;
    private volatile Instant refreshAfter = Instant.EPOCH;

    public InternalServiceTokenProvider(JwtEncoder encoder) { this.encoder = encoder; }

    public synchronized String bearerToken() {
        Instant now = Instant.now();
        if (refreshAfter.isAfter(now)) return token;
        JwtClaimsSet claims = JwtClaimsSet.builder().issuer("housekeeping-internal")
            .audience(java.util.List.of("housekeeping-business-api"))
            .subject("visits-service").issuedAt(now).expiresAt(now.plusSeconds(300))
            .claim("role", "INTERNAL").build();
        token = encoder.encode(JwtEncoderParameters.from(claims)).getTokenValue();
        refreshAfter = now.plusSeconds(240);
        return token;
    }
}
