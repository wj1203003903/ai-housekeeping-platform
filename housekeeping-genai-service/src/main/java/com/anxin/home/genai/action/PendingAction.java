package com.anxin.home.genai.action;

import java.time.Instant;
import java.util.Map;

public record PendingAction(String token, String userId, Type type,
                            Map<String, Object> payload, Instant expiresAt) {
    public enum Type { CREATE_BOOKING, CANCEL_BOOKING }
}
