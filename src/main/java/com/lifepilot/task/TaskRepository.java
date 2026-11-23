package com.lifepilot.task;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.time.OffsetDateTime;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TaskRepository extends JpaRepository<TaskEntity, UUID> {
    List<TaskEntity> findAllByOwner_IdOrderByCreatedAtDesc(UUID ownerId);
    List<TaskEntity> findAllByOwner_IdAndDueAtGreaterThanEqualAndDueAtLessThanOrderByDueAtAsc(
            UUID ownerId, OffsetDateTime start, OffsetDateTime end);
    List<TaskEntity> findAllByOwner_IdAndReminderEnabledTrueAndCompletedFalseAndReminderAtGreaterThanEqualAndReminderAtLessThanOrderByReminderAtAsc(
            UUID ownerId, OffsetDateTime start, OffsetDateTime end);
    Optional<TaskEntity> findByIdAndOwner_Id(UUID id, UUID ownerId);
    Optional<TaskEntity> findByOwner_IdAndClientId(UUID ownerId, String clientId);
}
