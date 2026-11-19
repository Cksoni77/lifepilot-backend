package com.lifepilot.calendar;

import com.lifepilot.security.CurrentUser;
import com.lifepilot.user.AppUserRepository;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class CalendarService {
    private final CalendarEventRepository events;
    private final AppUserRepository users;
    private final CurrentUser currentUser;

    public CalendarService(
            CalendarEventRepository events, AppUserRepository users, CurrentUser currentUser) {
        this.events = events;
        this.users = users;
        this.currentUser = currentUser;
    }

    @Transactional(readOnly = true)
    public List<CalendarDtos.EventResponse> list() {
        return events.findAllByOwner_IdOrderByStartsAtAsc(currentUser.id()).stream()
                .map(CalendarDtos.EventResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public CalendarDtos.ConflictResponse checkConflicts(
            OffsetDateTime startsAt, OffsetDateTime endsAt, UUID excludeEventId) {
        validateRange(startsAt, endsAt);
        List<CalendarDtos.EventResponse> conflicts = findConflicts(startsAt, endsAt).stream()
                .filter(event -> !event.getId().equals(excludeEventId))
                .map(CalendarDtos.EventResponse::from).toList();
        return new CalendarDtos.ConflictResponse(conflicts.isEmpty(), conflicts);
    }

    @Transactional
    public CalendarDtos.EventResponse create(CalendarDtos.EventRequest request) {
        validate(request);
        var owner = users.findById(currentUser.id()).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User not found"));
        return CalendarDtos.EventResponse.from(
                events.save(new CalendarEventEntity(owner, request)));
    }

    @Transactional
    public CalendarDtos.EventResponse update(UUID id, CalendarDtos.EventRequest request) {
        validate(request);
        CalendarEventEntity event = findOwned(id);
        event.update(request);
        return CalendarDtos.EventResponse.from(event);
    }

    @Transactional
    public void delete(UUID id) {
        events.delete(findOwned(id));
    }

    private List<CalendarEventEntity> findConflicts(
            OffsetDateTime startsAt, OffsetDateTime endsAt) {
        return events.findAllByOwner_IdAndStartsAtLessThanAndEndsAtGreaterThanOrderByStartsAtAsc(
                currentUser.id(), endsAt, startsAt);
    }

    private CalendarEventEntity findOwned(UUID id) {
        return events.findByIdAndOwner_Id(id, currentUser.id()).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "Calendar event not found"));
    }

    private void validate(CalendarDtos.EventRequest request) {
        validateRange(request.startsAt(), request.endsAt());
    }

    private void validateRange(OffsetDateTime startsAt, OffsetDateTime endsAt) {
        if (startsAt == null || endsAt == null || !endsAt.isAfter(startsAt)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Calendar event end must be after its start");
        }
    }
}
