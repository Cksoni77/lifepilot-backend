package com.lifepilot.ai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.lifepilot.calendar.CalendarEventRepository;
import com.lifepilot.common.SafeApiException;
import com.lifepilot.goal.GoalRepository;
import com.lifepilot.habit.HabitCompletionRepository;
import com.lifepilot.habit.HabitRepository;
import com.lifepilot.security.CurrentUser;
import com.lifepilot.task.TaskRepository;
import com.lifepilot.user.AppUserRepository;
import com.lifepilot.user.UserPreferencesDtos;
import com.lifepilot.user.UserPreferencesService;
import com.lifepilot.user.UserProfile;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.web.client.ResourceAccessException;

class AiQuickAddTest {
    @Mock private TaskRepository tasks;
    @Mock private HabitRepository habits;
    @Mock private GoalRepository goals;
    @Mock private HabitCompletionRepository completions;
    @Mock private CalendarEventRepository calendarEvents;
    @Mock private AppUserRepository users;
    @Mock private AiConversationRepository conversations;
    @Mock private AiMessageRepository messages;
    @Mock private OllamaClient ollama;
    @Mock private UserPreferencesService preferences;

    private AiService service;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        service = new AiService(tasks, habits, goals, completions, calendarEvents,
                new CurrentUser(), users, conversations, messages,
                new RecommendationEngine(com.lifepilot.TestAppProperties.defaults()),
                ollama, new ObjectMapper().registerModule(new JavaTimeModule()), preferences);
        when(preferences.get()).thenReturn(new UserPreferencesDtos.PreferencesResponse(
                "America/Los_Angeles", "system", true, true, null, null));
        when(preferences.getProfiles()).thenReturn(List.of(
                UserProfile.WORKING_PROFESSIONAL,
                UserProfile.JOB_SEEKER,
                UserProfile.BUSY_INDIVIDUAL));
        when(ollama.isEnabled()).thenReturn(true);
    }

    @Test
    void extractsMultipleProfileActivitiesWithoutSavingThem() {
        UUID requestId = UUID.randomUUID();
        when(ollama.quickAdd(anyString(), any(LocalDate.class), any(ZoneId.class), anyList()))
                .thenReturn("""
                        {
                          "activities": [
                            {"title":"Client meeting","description":"","profile":"WORKING_PROFESSIONAL",
                             "category":"WORK","activityType":"meeting","priority":"HIGH",
                             "dueDate":"2026-11-29","startAt":"2026-11-29T11:00:00-08:00",
                             "reminderAt":null,
                             "estimatedMinutes":null,"reminderSuggested":false,"confidence":0.92,
                             "fields":{"endAt":"2026-11-29T11:30:00-08:00","attendees":"Client"}},
                            {"title":"Prepare interview questions","description":"Java and Spring Boot",
                             "profile":"JOB_SEEKER","category":"WORK","activityType":"preparation",
                             "priority":"HIGH","dueDate":null,"startAt":null,"estimatedMinutes":45,
                             "reminderAt":null,
                             "reminderSuggested":false,"confidence":0.9,"fields":{"topics":"Java, Spring Boot"}},
                            {"title":"Pay electricity bill","description":"","profile":"BUSY_INDIVIDUAL",
                             "category":"PERSONAL","activityType":"bill","priority":"MEDIUM",
                             "dueDate":"2026-11-29","startAt":null,"estimatedMinutes":null,
                             "reminderAt":null,
                             "reminderSuggested":true,"confidence":0.95,
                             "fields":{"provider":"Electric utility","amount":"85.00","billDueDate":"2026-11-29"}}
                          ],
                          "requiresClarification":false,
                          "clarificationQuestion":null
                        }
                        """);

        AiDtos.QuickAddResponse response = service.quickAdd(
                new AiDtos.QuickAddRequest("Three activities", requestId));

        assertEquals(3, response.activities().size());
        assertEquals(requestId + "-0", response.activities().get(0).clientId());
        assertEquals(UserProfile.JOB_SEEKER, response.activities().get(1).profile());
        assertEquals("preparation", response.activities().get(1).activityType());
        assertEquals(true, response.activities().get(2).reminderSuggested());
        assertEquals(true, response.requiresClarification());
        assertEquals("What time would you like the reminder?", response.clarificationQuestion());
        verify(ollama).quickAdd(
                eq("Three activities"), any(LocalDate.class), eq(ZoneId.of("America/Los_Angeles")),
                eq(List.of("WORKING_PROFESSIONAL", "JOB_SEEKER", "BUSY_INDIVIDUAL")));
        verify(tasks, never()).save(any());
    }

    @Test
    void rejectsProfileAndCategoryValuesThatDoNotMatch() {
        when(ollama.quickAdd(anyString(), any(LocalDate.class), any(ZoneId.class), anyList()))
                .thenReturn("""
                        {"activities":[{"title":"Study","description":"","profile":"STUDENT",
                         "category":"PERSONAL","activityType":"assignment","priority":"MEDIUM",
                         "dueDate":null,"startAt":null,"reminderAt":null,"estimatedMinutes":30,
                         "reminderSuggested":false,"confidence":0.8,"fields":{}}],
                         "requiresClarification":false,"clarificationQuestion":null}
                        """);

        SafeApiException error = assertThrows(SafeApiException.class,
                () -> service.quickAdd(new AiDtos.QuickAddRequest(
                        "Study", UUID.randomUUID())));

        assertEquals(org.springframework.http.HttpStatus.BAD_GATEWAY, error.status());
    }

    @Test
    void returnsClarificationWithoutGuessingWhenModelRequestsIt() {
        when(ollama.quickAdd(anyString(), any(LocalDate.class), any(ZoneId.class), anyList()))
                .thenReturn("""
                        {"activities":[],"requiresClarification":true,
                         "clarificationQuestion":"Which Monday do you mean?"}
                        """);

        AiDtos.QuickAddResponse response = service.quickAdd(
                new AiDtos.QuickAddRequest("Interview Monday", UUID.randomUUID()));

        assertEquals(true, response.requiresClarification());
        assertEquals("Which Monday do you mean?", response.clarificationQuestion());
        assertEquals(List.of(), response.activities());
    }

    @Test
    void meetingWithoutCompleteTimeRequestsClarification() {
        when(ollama.quickAdd(anyString(), any(LocalDate.class), any(ZoneId.class), anyList()))
                .thenReturn("""
                        {"activities":[{"title":"Client meeting","description":"",
                         "profile":"WORKING_PROFESSIONAL","category":"WORK","activityType":"meeting",
                         "priority":"HIGH","dueDate":"2026-11-29","startAt":null,"reminderAt":null,
                         "estimatedMinutes":null,"reminderSuggested":false,"confidence":0.92,
                         "fields":{}}],
                         "requiresClarification":false,"clarificationQuestion":null}
                        """);

        AiDtos.QuickAddResponse response = service.quickAdd(
                new AiDtos.QuickAddRequest("Meeting with client", UUID.randomUUID()));

        assertEquals(true, response.requiresClarification());
        assertEquals("What start and end time should the meeting use?",
                response.clarificationQuestion());
    }

    @Test
    void reportsLocalModelUnavailableWithoutPersisting() {
        when(ollama.quickAdd(anyString(), any(LocalDate.class), any(ZoneId.class), anyList()))
                .thenThrow(new ResourceAccessException("Connection refused"));

        SafeApiException error = assertThrows(SafeApiException.class,
                () -> service.quickAdd(new AiDtos.QuickAddRequest(
                        "Pay bill", UUID.randomUUID())));

        assertEquals(org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE, error.status());
        verify(tasks, never()).save(any());
    }

    @Test
    void rejectsMalformedOllamaJsonWithoutPersisting() {
        when(ollama.quickAdd(anyString(), any(LocalDate.class), any(ZoneId.class), anyList()))
                .thenReturn("{not-valid-json");

        SafeApiException error = assertThrows(SafeApiException.class,
                () -> service.quickAdd(new AiDtos.QuickAddRequest(
                        "Create an activity", UUID.randomUUID())));

        assertEquals(org.springframework.http.HttpStatus.BAD_GATEWAY, error.status());
        verify(tasks, never()).save(any());
    }
}
