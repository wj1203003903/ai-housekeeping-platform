package com.anxin.home.genai.security;

import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class UserContextResolver {
    private final HousekeepingSecurityProperties properties;

    public UserContextResolver(HousekeepingSecurityProperties properties) {
        this.properties = properties;
    }

    public UserContext resolve(Jwt jwt, String requestedConversationId) {
        String userId = jwt == null ? "1" : jwt.getSubject();
        UserContext.Role role = jwt == null ? UserContext.Role.CUSTOMER
            : UserContext.Role.from(jwt.getClaimAsString("role"));
        String conversationId = normalizeConversationId(requestedConversationId, userId);
        return new UserContext(userId, role, conversationId);
    }

    public String currentUserId(Jwt jwt) {
        return jwt == null && !properties.isEnabled() ? "1" : jwt.getSubject();
    }

    private String normalizeConversationId(String value, String userId) {
        String conversation = value == null || value.isBlank() ? UUID.randomUUID().toString() : value.trim();
        if (!conversation.matches("[A-Za-z0-9_-]{1,80}")) {
            throw new IllegalArgumentException("conversationId format is invalid");
        }
        return userId + ":" + conversation;
    }
}
