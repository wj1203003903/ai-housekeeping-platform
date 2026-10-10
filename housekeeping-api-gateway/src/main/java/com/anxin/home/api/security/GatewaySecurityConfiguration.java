package com.anxin.home.api.security;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.http.HttpMethod;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.NimbusReactiveJwtDecoder;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.security.oauth2.jwt.JwtClaimValidator;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.oauth2.server.resource.authentication.ReactiveJwtAuthenticationConverter;
import org.springframework.security.web.server.SecurityWebFilterChain;
import reactor.core.publisher.Flux;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.List;

@Configuration
@EnableConfigurationProperties(GatewaySecurityProperties.class)
public class GatewaySecurityConfiguration {

    @Bean
    SecurityWebFilterChain gatewaySecurityWebFilterChain(ServerHttpSecurity http,
                                                          GatewaySecurityProperties properties) {
        http.csrf(ServerHttpSecurity.CsrfSpec::disable)
            .authorizeExchange(exchange -> exchange
                .pathMatchers("/", "/index.html", "/css/**", "/scripts/**", "/images/**",
                    "/fonts/**", "/webjars/**", "/actuator/health", "/api/genai/auth/token",
                    "/api/genai/auth/refresh", "/api/genai/auth/logout",
                    "/api/genai/auth/register/**", "/api/genai/auth/registration-options")
                .permitAll()
                .pathMatchers(HttpMethod.POST, "/api/customer/**", "/api/vet/**").hasRole("ADMIN")
                .pathMatchers(HttpMethod.GET, "/api/customer/customers").hasRole("ADMIN")
                .pathMatchers(HttpMethod.POST, "/api/visit/bookings").hasAnyRole("CUSTOMER", "ADMIN")
                .pathMatchers(HttpMethod.POST, "/api/visit/bookings/*/accept",
                    "/api/visit/bookings/*/start", "/api/visit/bookings/*/complete")
                .hasAnyRole("WORKER", "ADMIN")
                .pathMatchers(HttpMethod.POST, "/api/visit/bookings/*/assign").hasRole("ADMIN")
                .pathMatchers(HttpMethod.POST, "/api/visit/bookings/*/cancel")
                .hasAnyRole("CUSTOMER", "ADMIN")
                .pathMatchers("/api/**").authenticated()
                .pathMatchers("/actuator/**").hasRole("ADMIN")
                .anyExchange().denyAll())
            .oauth2ResourceServer(resourceServer -> resourceServer
                .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter())));
        return http.build();
    }

    private ReactiveJwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtGrantedAuthoritiesConverter authorities = new JwtGrantedAuthoritiesConverter();
        authorities.setAuthoritiesClaimName("role");
        authorities.setAuthorityPrefix("ROLE_");
        ReactiveJwtAuthenticationConverter converter = new ReactiveJwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(jwt -> Flux.fromIterable(authorities.convert(jwt)));
        return converter;
    }

    @Bean
    ReactiveJwtDecoder gatewayJwtDecoder(GatewaySecurityProperties properties) {
        if (properties.getSecret() == null || properties.getSecret().isBlank()) {
            throw new IllegalStateException("HOUSEKEEPING_JWT_SECRET must be configured");
        }
        byte[] keyBytes = properties.getSecret().getBytes(StandardCharsets.UTF_8);
        if (keyBytes.length < 32) {
            throw new IllegalStateException("housekeeping.security.secret must contain at least 32 bytes");
        }
        SecretKey secretKey = new SecretKeySpec(keyBytes, "HmacSHA256");
        NimbusReactiveJwtDecoder decoder = NimbusReactiveJwtDecoder.withSecretKey(secretKey)
            .macAlgorithm(MacAlgorithm.HS256)
            .build();
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
            JwtValidators.createDefaultWithIssuer(properties.getIssuer()),
            new JwtClaimValidator<List<String>>("aud", audience -> audience != null
                && audience.contains("housekeeping-api"))));
        return decoder;
    }
}
