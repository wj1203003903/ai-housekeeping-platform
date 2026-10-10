package com.anxin.home.genai.memory;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "user_memories")
public class UserMemory {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "user_id", nullable = false, length = 100)
    private String userId;
    @Column(name = "kind", nullable = false, length = 30)
    private String kind;
    @Column(name = "content", nullable = false, length = 2000)
    private String content;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected UserMemory() {}
    public UserMemory(String userId, String kind, String content) {
        this.userId = userId;
        this.kind = kind;
        this.content = content;
        this.createdAt = Instant.now();
    }
    public Long getId() { return id; }
    public String getKind() { return kind; }
    public String getContent() { return content; }
    public Instant getCreatedAt() { return createdAt; }
}
