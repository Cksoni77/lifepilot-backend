package com.lifepilot.calendar;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.lifepilot.security.CurrentUser;
import com.lifepilot.user.AppUser;
import com.lifepilot.user.AppUserRepository;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.server.ResponseStatusException;

class CalendarServiceTest {
    @Mock private CalendarEventRepository events;
    @Mock private AppUserRepository users;

    private AutoCloseable mocks;
    private UUID authenticatedUser;
    private CalendarService service;

    @BeforeEach
    void setUp() {
        mocks = MockitoAnnotations.openMocks(this);
        authenticatedUser = UUID.randomUUID();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        authenticatedUser, null, java.util.List.of()));
        service = new CalendarService(events, users, new CurrentUser());
    }

    @AfterEach
    void tearDown() throws Exception {
        SecurityContextHolder.clearContext();
        mocks.close();
    }

    @Test
    void conflictCheckIsScopedToCurrentUserAndUsesHalfOpenTimeRanges() {
        OffsetDateTime startsAt = OffsetDateTime.of(
                2026, 11, 5, 10, 0, 0, 0, ZoneOffset.UTC);
        OffsetDateTime endsAt = startsAt.plusHours(1);
        AppUser owner = new AppUser("owner@example.com", "Owner", "password");
        CalendarEventEntity conflict = new CalendarEventEntity(owner,
                new CalendarDtos.EventRequest(
                        "Existing", startsAt.plusMinutes(30), endsAt.plusMinutes(30),
                        true, "manual"));
        when(events
                .findAllByOwner_IdAndStartsAtLessThanAndEndsAtGreaterThanOrderByStartsAtAsc(
                        authenticatedUser, endsAt, startsAt))
                .thenReturn(List.of(conflict));

        CalendarDtos.ConflictResponse response =
                service.checkConflicts(startsAt, endsAt, null);

        assertFalse(response.available());
        assertEquals(List.of(conflict.getId()),
                response.conflicts().stream().map(CalendarDtos.EventResponse::id).toList());
        verify(events)
                .findAllByOwner_IdAndStartsAtLessThanAndEndsAtGreaterThanOrderByStartsAtAsc(
                        authenticatedUser, endsAt, startsAt);
    }

    @Test
    void conflictCheckCanExcludeTheEventBeingRescheduled() {
        OffsetDateTime startsAt = OffsetDateTime.now(ZoneOffset.UTC);
        OffsetDateTime endsAt = startsAt.plusHours(1);
        AppUser owner = new AppUser("owner@example.com", "Owner", "password");
        CalendarEventEntity event = new CalendarEventEntity(owner,
                new CalendarDtos.EventRequest("Existing", startsAt, endsAt, true, "manual"));
        when(events
                .findAllByOwner_IdAndStartsAtLessThanAndEndsAtGreaterThanOrderByStartsAtAsc(
                        authenticatedUser, endsAt, startsAt))
                .thenReturn(List.of(event));

        CalendarDtos.ConflictResponse response =
                service.checkConflicts(startsAt, endsAt, event.getId());

        assertTrue(response.available());
        assertTrue(response.conflicts().isEmpty());
    }

    @Test
    void rejectsNonPositiveEventRangesBeforeQueryingTheRepository() {
        OffsetDateTime instant = OffsetDateTime.now(ZoneOffset.UTC);

        assertThrows(ResponseStatusException.class,
                () -> service.checkConflicts(instant, instant, null));
    }
}
