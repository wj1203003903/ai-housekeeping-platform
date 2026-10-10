package com.anxin.home.visits.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

@Entity
@Table(name = "domain_event_outbox")
public class DomainEventOutbox {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "aggregate_id", nullable = false, length = 80)
    private String aggregateId;
    @Column(name = "event_type", nullable = false, length = 80)
    private String eventType;
    @Column(nullable = false, length = 4000)
    private String payload;
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
    @Column(name = "published_at")
    private LocalDateTime publishedAt;

    protected DomainEventOutbox() {}
    public DomainEventOutbox(String aggregateId, String eventType, String payload) {
        this.aggregateId = aggregateId; this.eventType = eventType; this.payload = payload;
        this.createdAt = LocalDateTime.now();
    }
    public Long getId() { return id; }
    public String getAggregateId() { return aggregateId; }
    public String getEventType() { return eventType; }
    public String getPayload() { return payload; }
    public void markPublished() { this.publishedAt = LocalDateTime.now(); }
}
