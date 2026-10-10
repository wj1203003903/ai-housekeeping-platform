package com.anxin.home.genai.memory;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties("housekeeping.memory")
public class MemoryProperties {

    private int maxMessages = 20;
    private Duration redisTtl = Duration.ofHours(24);
    private String redisKeyPrefix = "housekeeping:chat:";
    private boolean semanticEnabled;
    private int semanticTopK = 5;

    public int getMaxMessages() { return maxMessages; }
    public void setMaxMessages(int maxMessages) { this.maxMessages = maxMessages; }
    public Duration getRedisTtl() { return redisTtl; }
    public void setRedisTtl(Duration redisTtl) { this.redisTtl = redisTtl; }
    public String getRedisKeyPrefix() { return redisKeyPrefix; }
    public void setRedisKeyPrefix(String redisKeyPrefix) { this.redisKeyPrefix = redisKeyPrefix; }
    public boolean isSemanticEnabled() { return semanticEnabled; }
    public void setSemanticEnabled(boolean semanticEnabled) { this.semanticEnabled = semanticEnabled; }
    public int getSemanticTopK() { return semanticTopK; }
    public void setSemanticTopK(int semanticTopK) { this.semanticTopK = semanticTopK; }
}
