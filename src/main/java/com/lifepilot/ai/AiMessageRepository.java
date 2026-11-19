package com.lifepilot.ai;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AiMessageRepository extends JpaRepository<AiMessageEntity, UUID> {
    List<AiMessageEntity> findAllByConversation_IdOrderByCreatedAtAsc(UUID conversationId);

    List<AiMessageEntity> findTop10ByConversation_IdOrderByCreatedAtDesc(UUID conversationId);
}
