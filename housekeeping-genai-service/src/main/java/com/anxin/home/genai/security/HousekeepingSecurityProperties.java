package com.anxin.home.genai.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

@ConfigurationProperties("housekeeping.security")
public class HousekeepingSecurityProperties {

    private boolean enabled = true;
    private String issuer = "housekeeping-platform";
    private String secret;
    private String internalSecret;
    private boolean secureCookies = true;
    private Duration tokenTtl = Duration.ofMinutes(15);
    private Map<String, DemoUser> users = new LinkedHashMap<>();

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public String getIssuer() { return issuer; }
    public void setIssuer(String issuer) { this.issuer = issuer; }
    public String getSecret() { return secret; }
    public void setSecret(String secret) { this.secret = secret; }
    public String getInternalSecret() { return internalSecret; }
    public void setInternalSecret(String internalSecret) { this.internalSecret = internalSecret; }
    public boolean isSecureCookies() { return secureCookies; }
    public void setSecureCookies(boolean secureCookies) { this.secureCookies = secureCookies; }
    public Duration getTokenTtl() { return tokenTtl; }
    public void setTokenTtl(Duration tokenTtl) { this.tokenTtl = tokenTtl; }
    public Map<String, DemoUser> getUsers() { return users; }
    public void setUsers(Map<String, DemoUser> users) { this.users = users; }

    public static class DemoUser {
        private String password;
        private String userId;
        private String role = "CUSTOMER";

        public String getPassword() { return password; }
        public void setPassword(String password) { this.password = password; }
        public String getUserId() { return userId; }
        public void setUserId(String userId) { this.userId = userId; }
        public String getRole() { return role; }
        public void setRole(String role) { this.role = role; }
    }
}
