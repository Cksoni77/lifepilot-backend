package com.lifepilot.calendar;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CalendarEventRepository extends JpaRepository<CalendarEventEntity, UUID> {
    List<CalendarEventEntity> findAllByOwner_IdOrderByStartsAtAsc(UUID ownerId);

    List<CalendarEventEntity> findTop50ByOwner_IdAndEndsAtAfterOrderByStartsAtAsc(
            UUID ownerId, OffsetDateTime instant);

    List<CalendarEventEntity>
            findAllByOwner_IdAndStartsAtLessThanAndEndsAtGreaterThanOrderByStartsAtAsc(
                    UUID ownerId, OffsetDateTime end, OffsetDateTime start);

    Optional<CalendarEventEntity> findByIdAndOwner_Id(UUID id, UUID ownerId);
}
