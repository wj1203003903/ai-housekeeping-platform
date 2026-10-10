package com.anxin.home.genai.action;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class PendingActionStore {
    private static final Logger LOG = LoggerFactory.getLogger(PendingActionStore.class);
    private static final Duration TTL = Duration.ofMinutes(10);
    private static final String PREFIX = "housekeeping:pending-action:";

    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;
    private final Map<String, PendingAction> fallback = new ConcurrentHashMap<>();

    public PendingActionStore(StringRedisTemplate redis, ObjectMapper objectMapper) {
        this.redis = redis;
        this.objectMapper = objectMapper;
    }

    public PendingAction create(String userId, PendingAction.Type type, Map<String, Object> payload) {
        String token = UUID.randomUUID().toString();
        PendingAction action = new PendingAction(token, userId, type, Map.copyOf(payload), Instant.now().plus(TTL));
        fallback.put(token, action);
        try {
            redis.opsForValue().set(redisKey(userId, token), objectMapper.writeValueAsString(action), TTL);
        } catch (RuntimeException | JsonProcessingException exception) {
            LOG.warn("Redis unavailable; pending action is stored in this instance only", exception);
        }
        return action;
    }

    public Optional<PendingAction> consume(String token, String userId) {
        PendingAction action = null;
        try {
            String json = redis.opsForValue().getAndDelete(redisKey(userId, token));
            if (json != null) action = objectMapper.readValue(json, PendingAction.class);
        } catch (RuntimeException | JsonProcessingException exception) {
            LOG.warn("Redis unavailable while consuming pending action", exception);
        }
        if (action == null) {
            PendingAction candidate = fallback.get(token);
            if (candidate != null && candidate.userId().equals(userId)) action = fallback.remove(token);
        } else {
            fallback.remove(token);
        }
        if (action == null || action.expiresAt().isBefore(Instant.now()) || !action.userId().equals(userId)) {
            return Optional.empty();
        }
        return Optional.of(action);
    }

    private String redisKey(String userId, String token) {
        return PREFIX + userId.replaceAll("[^A-Za-z0-9_-]", "_") + ":" + token;
    }
}
