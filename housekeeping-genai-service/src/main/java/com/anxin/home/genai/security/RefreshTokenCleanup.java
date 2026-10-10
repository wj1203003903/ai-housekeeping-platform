package com.anxin.home.genai.security;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Component
class RefreshTokenCleanup {
    private final RefreshTokenRepository repository;

    RefreshTokenCleanup(RefreshTokenRepository repository) {
        this.repository = repository;
    }

    @Scheduled(fixedDelayString = "${housekeeping.security.refresh-cleanup-delay:1h}")
    @Transactional
    void removeExpiredAndRevokedTokens() {
        repository.deleteByExpiresAtBeforeOrRevokedAtIsNotNull(Instant.now());
    }
}
