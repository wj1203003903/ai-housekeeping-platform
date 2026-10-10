package com.anxin.home.genai.security;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "platform_accounts")
public class PlatformAccount {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 50)
    private String username;

    @Column(name = "password_hash", nullable = false, length = 255)
    private String passwordHash;

    @Column(name = "user_id", nullable = false, length = 50)
    private String userId;

    @Column(nullable = false, length = 20)
    private String role;

    @Column(nullable = false)
    private boolean enabled;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "failed_attempts", nullable = false)
    private int failedAttempts;

    @Column(name = "locked_until")
    private Instant lockedUntil;

    @Column(name = "token_version", nullable = false)
    private int tokenVersion;

    protected PlatformAccount() {
    }

    public PlatformAccount(String username, String passwordHash, String userId, String role) {
        this.username = username;
        this.passwordHash = passwordHash;
        this.userId = userId;
        this.role = role;
        this.enabled = true;
        this.createdAt = Instant.now();
        this.failedAttempts = 0;
        this.tokenVersion = 0;
    }

    public String getUsername() { return username; }
    public String getPasswordHash() { return passwordHash; }
    public String getUserId() { return userId; }
    public String getRole() { return role; }
    public boolean isEnabled() { return enabled; }
    public int getTokenVersion() { return tokenVersion; }
    public boolean isLocked() { return lockedUntil != null && lockedUntil.isAfter(Instant.now()); }
    public void recordFailedLogin() {
        failedAttempts++;
        if (failedAttempts >= 5) {
            lockedUntil = Instant.now().plusSeconds(15 * 60);
            failedAttempts = 0;
        }
    }
    public void recordSuccessfulLogin() { failedAttempts = 0; lockedUntil = null; }
    public void changePassword(String passwordHash) { this.passwordHash = passwordHash; this.tokenVersion++; }
}
