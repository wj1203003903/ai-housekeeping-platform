package com.anxin.home.genai.audit;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "ai_audit_events")
public class AiAuditEvent {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "user_id", nullable = false, length = 100)
    private String userId;
    @Column(name = "conversation_id", length = 100)
    private String conversationId;
    @Column(name = "event_type", nullable = false, length = 50)
    private String eventType;
    @Column(name = "outcome", nullable = false, length = 30)
    private String outcome;
    @Column(name = "details", length = 4000)
    private String details;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected AiAuditEvent() {}

    public AiAuditEvent(String userId, String conversationId, String eventType, String outcome, String details) {
        this.userId = userId;
        this.conversationId = conversationId;
        this.eventType = eventType;
        this.outcome = outcome;
        this.details = details;
        this.createdAt = Instant.now();
    }
}
