package com.lifepilot.ai;

import com.lifepilot.common.BaseEntity;
import com.lifepilot.user.AppUser;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.util.UUID;

@Entity
@Table(name = "ai_conversations")
public class AiConversationEntity extends BaseEntity {
    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private AppUser owner;

    @Column(length = 200)
    private String title;

    protected AiConversationEntity() {}

    public AiConversationEntity(AppUser owner, String title) {
        id = UUID.randomUUID();
        this.owner = owner;
        this.title = title == null ? null
                : title.substring(0, Math.min(title.length(), 200));
    }

    public UUID getId() { return id; }
    public AppUser getOwner() { return owner; }
    public String getTitle() { return title; }
    public void markActivity() { markUpdated(); }
}
