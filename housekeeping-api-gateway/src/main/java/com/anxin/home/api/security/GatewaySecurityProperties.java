package com.anxin.home.api.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("housekeeping.security")
public class GatewaySecurityProperties {

    private boolean enabled = true;
    private String secret;
    private String issuer = "housekeeping-platform";

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public String getSecret() { return secret; }
    public void setSecret(String secret) { this.secret = secret; }
    public String getIssuer() { return issuer; }
    public void setIssuer(String issuer) { this.issuer = issuer; }
}
