package com.lifepilot.ai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
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
                new RecommendationEngine(0.30, 0.30, 0.20, 0.20),
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
}
