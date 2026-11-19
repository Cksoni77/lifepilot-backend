package com.lifepilot.calendar;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public final class CalendarDtos {
    private CalendarDtos() {}

    public record EventRequest(
            @NotBlank @Size(max = 200) String title,
            @NotNull OffsetDateTime startsAt,
            @NotNull OffsetDateTime endsAt,
            boolean fixed,
            @Size(max = 30) String source) {}

    public record EventResponse(
            UUID id, String title, OffsetDateTime startsAt, OffsetDateTime endsAt,
            boolean fixed, String source) {
        static EventResponse from(CalendarEventEntity event) {
            return new EventResponse(event.getId(), event.getTitle(), event.getStartsAt(),
                    event.getEndsAt(), event.isFixed(), event.getSource());
        }
    }

    public record ConflictResponse(boolean available, List<EventResponse> conflicts) {}
}
