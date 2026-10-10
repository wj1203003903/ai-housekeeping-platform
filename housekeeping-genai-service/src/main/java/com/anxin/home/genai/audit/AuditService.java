package com.anxin.home.genai.audit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class AuditService {
    private static final Logger LOG = LoggerFactory.getLogger(AuditService.class);
    private final AiAuditRepository repository;

    public AuditService(AiAuditRepository repository) { this.repository = repository; }

    public void record(String userId, String conversationId, String type, String outcome, String details) {
        try {
            repository.save(new AiAuditEvent(userId, conversationId, type, outcome, redact(details)));
        } catch (RuntimeException exception) {
            LOG.error("Could not persist AI audit event type={} outcome={}", type, outcome, exception);
        }
    }

    private String redact(String value) {
        if (value == null) return null;
        String safe = value.replaceAll("(?i)(password|token|secret)\\s*[=:]\\s*[^,}\\s]+", "$1=[REDACTED]");
        return safe.length() > 4000 ? safe.substring(0, 4000) : safe;
    }
}
