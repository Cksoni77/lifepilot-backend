package com.lifepilot.ai;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

@Entity
@Table(name = "ai_messages")
public class AiMessageEntity {
    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "conversation_id", nullable = false)
    private AiConversationEntity conversation;

    @Column(nullable = false, length = 20)
    private String role;

    @Column(nullable = false, columnDefinition = "text")
    private String content;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    protected AiMessageEntity() {}

    public AiMessageEntity(AiConversationEntity conversation, String role, String content) {
        id = UUID.randomUUID();
        this.conversation = conversation;
        this.role = role;
        this.content = content;
        createdAt = OffsetDateTime.now(ZoneOffset.UTC);
    }

    public UUID getId() { return id; }
    public AiConversationEntity getConversation() { return conversation; }
    public String getRole() { return role; }
    public String getContent() { return content; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
}
