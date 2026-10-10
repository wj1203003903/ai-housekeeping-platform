package com.anxin.home.api.security;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.util.UUID;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestIdFilter implements WebFilter {
    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        String incoming = exchange.getRequest().getHeaders().getFirst("X-Request-Id");
        String requestId = incoming != null && incoming.matches("[A-Za-z0-9_-]{8,80}")
            ? incoming : UUID.randomUUID().toString();
        ServerWebExchange updated = exchange.mutate().request(request -> request.headers(headers -> {
            headers.remove("X-Internal-Service-Key");
            headers.set("X-Request-Id", requestId);
        })).build();
        updated.getResponse().getHeaders().set("X-Request-Id", requestId);
        return chain.filter(updated);
    }
}
