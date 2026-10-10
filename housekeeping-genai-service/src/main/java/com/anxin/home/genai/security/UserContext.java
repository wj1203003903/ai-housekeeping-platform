package com.anxin.home.genai.security;

public record UserContext(String userId, Role role, String conversationId) {

    public enum Role {
        CUSTOMER,
        WORKER,
        ADMIN;

        public static Role from(String value) {
            if (value == null || value.isBlank()) {
                return CUSTOMER;
            }
            return Role.valueOf(value.trim().toUpperCase());
        }
    }
}
