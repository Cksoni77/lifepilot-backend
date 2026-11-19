package com.lifepilot.ai;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AiConversationRepository
        extends JpaRepository<AiConversationEntity, UUID> {
    List<AiConversationEntity> findAllByOwner_IdOrderByUpdatedAtDesc(UUID ownerId);
    Optional<AiConversationEntity> findByIdAndOwner_Id(UUID id, UUID ownerId);
}
