package com.anxin.home.api.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.net.InetSocketAddress;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 20)
public class AiRateLimitFilter implements WebFilter {
    private final ReactiveStringRedisTemplate redis;
    private final int requestsPerMinute;
    private final int authRequestsPerMinute;
    private final Map<String, LocalCounter> fallback = new ConcurrentHashMap<>();

    public AiRateLimitFilter(ObjectProvider<ReactiveStringRedisTemplate> redisProvider,
                             @Value("${housekeeping.rate-limit.ai-per-minute:20}") int requestsPerMinute,
                             @Value("${housekeeping.rate-limit.auth-per-minute:10}") int authRequestsPerMinute) {
        this.redis = redisProvider.getIfAvailable();
        this.requestsPerMinute = requestsPerMinute;
        this.authRequestsPerMinute = authRequestsPerMinute;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        String path = exchange.getRequest().getPath().value();
        boolean aiRequest = path.equals("/api/genai/chatclient");
        boolean authRequest = path.equals("/api/genai/auth/token") || path.startsWith("/api/genai/auth/register/")
            || path.equals("/api/genai/auth/refresh") || path.equals("/api/genai/auth/logout")
            || path.equals("/api/genai/auth/registration-options");
        if (!aiRequest && !authRequest) return chain.filter(exchange);
        int limit = aiRequest ? requestsPerMinute : authRequestsPerMinute;
        String bucket = aiRequest ? "ai" : "auth";
        String key = "housekeeping:rate:" + bucket + ":" + clientKey(exchange) + ":" + (Instant.now().getEpochSecond() / 60);
        if (redis == null) {
            return localAllow(key, limit) ? chain.filter(exchange) : tooManyRequests(exchange);
        }
        return redis.opsForValue().increment(key)
            .flatMap(count -> redis.expire(key, Duration.ofMinutes(2)).thenReturn(count))
            .map(count -> count <= limit)
            .onErrorResume(error -> Mono.just(localAllow(key, limit)))
            .flatMap(allowed -> allowed ? chain.filter(exchange) : tooManyRequests(exchange));
    }

    private boolean localAllow(String key, int limit) {
        fallback.keySet().removeIf(existing -> !existing.endsWith(key.substring(key.lastIndexOf(':'))));
        return fallback.computeIfAbsent(key, ignored -> new LocalCounter()).value.incrementAndGet() <= limit;
    }

    private String clientKey(ServerWebExchange exchange) {
        InetSocketAddress remote = exchange.getRequest().getRemoteAddress();
        String raw = remote == null || remote.getAddress() == null
            ? "unknown" : remote.getAddress().getHostAddress();
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(raw.getBytes(StandardCharsets.UTF_8))).substring(0, 24);
        } catch (Exception exception) {
            return Integer.toHexString(raw.hashCode());
        }
    }

    private Mono<Void> tooManyRequests(ServerWebExchange exchange) {
        exchange.getResponse().setStatusCode(HttpStatus.TOO_MANY_REQUESTS);
        exchange.getResponse().getHeaders().set("Retry-After", "60");
        return exchange.getResponse().setComplete();
    }

    private static final class LocalCounter { private final AtomicInteger value = new AtomicInteger(); }
}
