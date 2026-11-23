package com.lifepilot.ai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lifepilot.TestAppProperties;
import com.lifepilot.calendar.CalendarEventRepository;
import com.lifepilot.goal.GoalRepository;
import com.lifepilot.habit.HabitCompletionRepository;
import com.lifepilot.habit.HabitRepository;
import com.lifepilot.security.CurrentUser;
import com.lifepilot.task.TaskRepository;
import com.lifepilot.user.AppUserRepository;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.server.ResponseStatusException;

class AiConversationAuthorizationTest {
    @Mock private TaskRepository tasks;
    @Mock private HabitRepository habits;
    @Mock private GoalRepository goals;
    @Mock private HabitCompletionRepository completions;
    @Mock private CalendarEventRepository calendarEvents;
    @Mock private AppUserRepository users;
    @Mock private AiConversationRepository conversations;
    @Mock private AiMessageRepository messages;
    @Mock private OpenAiClient openAi;

    private AutoCloseable mocks;
    private UUID authenticatedUser;
    private AiService service;

    @BeforeEach
    void setUp() {
        mocks = MockitoAnnotations.openMocks(this);
        authenticatedUser = UUID.randomUUID();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        authenticatedUser, null, java.util.List.of()));
        service = new AiService(tasks, habits, goals, completions, calendarEvents,
                new CurrentUser(), users, conversations, messages,
                new RecommendationEngine(TestAppProperties.defaults()),
                openAi, new ObjectMapper());
    }

    @AfterEach
    void tearDown() throws Exception {
        SecurityContextHolder.clearContext();
        mocks.close();
    }

    @Test
    void messageHistoryCannotBeReadByAnotherUser() {
        UUID conversationId = UUID.randomUUID();
        when(conversations.findByIdAndOwner_Id(conversationId, authenticatedUser))
                .thenReturn(Optional.empty());

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class, () -> service.messages(conversationId));

        assertEquals(HttpStatus.NOT_FOUND, exception.getStatusCode());
        verify(conversations).findByIdAndOwner_Id(conversationId, authenticatedUser);
    }

    @Test
    void aiResponseValidationDropsForeignTaskIdsAndRequiresConfirmationForActions() {
        prepareChatMocks();
        UUID foreignTaskId = UUID.randomUUID();
        when(openAi.isConfigured()).thenReturn(true);
        when(openAi.respond(org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString())).thenReturn(
                        """
                        {"message":"Suggested plan","intent":"planning",
                         "relevantTaskIds":["%s"],"suggestedActions":["reschedule a task"],
                         "requiresConfirmation":false}
                        """.formatted(foreignTaskId));

        AiDtos.ChatResponse response = service.chat(
                new AiDtos.ChatRequest("Help me plan", null));

        assertEquals(java.util.List.of(), response.relevantTaskIds());
        assertEquals(java.util.List.of("reschedule a task"), response.suggestedActions());
        org.junit.jupiter.api.Assertions.assertTrue(response.requiresConfirmation());
    }

    @Test
    void invalidAiResponseFallsBackWithoutExposingProviderDetails() {
        prepareChatMocks();
        when(openAi.isConfigured()).thenReturn(true);
        when(openAi.respond(org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString()))
                .thenReturn("{\"message\":\" \"}");

        AiDtos.ChatResponse response = service.chat(
                new AiDtos.ChatRequest("Help me plan", null));

        assertEquals(false, response.providerAvailable());
        org.junit.jupiter.api.Assertions.assertTrue(
                response.message().contains("AI chat is not configured"));
    }

    @Test
    void missingProviderKeyUsesDeterministicReplyWithoutCallingProvider() {
        prepareChatMocks();
        when(openAi.isConfigured()).thenReturn(false);

        AiDtos.ChatResponse response = service.chat(
                new AiDtos.ChatRequest("What tasks are due today?", null));

        assertEquals(false, response.providerAvailable());
        assertEquals("tasks_today", response.intent());
        verify(openAi, never()).respond(
                org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString());
    }

    private void prepareChatMocks() {
        when(users.findById(authenticatedUser))
                .thenReturn(Optional.of(new com.lifepilot.user.AppUser(
                        "person@example.com", "Person", "password-hash")));
        when(conversations.save(org.mockito.ArgumentMatchers.any(AiConversationEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(messages.findTop10ByConversation_IdOrderByCreatedAtDesc(
                org.mockito.ArgumentMatchers.any())).thenReturn(java.util.List.of());
        when(tasks.findAllByOwner_IdOrderByCreatedAtDesc(authenticatedUser))
                .thenReturn(java.util.List.of());
        when(habits.findAllByOwner_IdOrderByCreatedAtDesc(authenticatedUser))
                .thenReturn(java.util.List.of());
        when(goals.findAllByOwner_IdOrderByCreatedAtDesc(authenticatedUser))
                .thenReturn(java.util.List.of());
        when(calendarEvents.findTop50ByOwner_IdAndEndsAtAfterOrderByStartsAtAsc(
                org.mockito.ArgumentMatchers.eq(authenticatedUser),
                org.mockito.ArgumentMatchers.any())).thenReturn(java.util.List.of());
    }
}
