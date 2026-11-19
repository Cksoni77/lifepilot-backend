package com.lifepilot.task;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.lifepilot.goal.GoalRepository;
import com.lifepilot.security.CurrentUser;
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

class TaskServiceAuthorizationTest {
    @Mock private TaskRepository tasks;
    @Mock private AppUserRepository users;
    @Mock private GoalRepository goals;

    private AutoCloseable mocks;
    private UUID authenticatedUser;
    private TaskService service;

    @BeforeEach
    void setUp() {
        mocks = MockitoAnnotations.openMocks(this);
        authenticatedUser = UUID.randomUUID();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(authenticatedUser, null, java.util.List.of()));
        service = new TaskService(tasks, users, goals, new CurrentUser());
    }

    @AfterEach
    void tearDown() throws Exception {
        SecurityContextHolder.clearContext();
        mocks.close();
    }

    @Test
    void taskLookupUsesAuthenticatedUserIdAndHidesOtherUsersTasks() {
        UUID taskId = UUID.randomUUID();
        when(tasks.findByIdAndOwner_Id(taskId, authenticatedUser)).thenReturn(Optional.empty());

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class, () -> service.get(taskId));

        assertEquals(HttpStatus.NOT_FOUND, exception.getStatusCode());
        verify(tasks).findByIdAndOwner_Id(taskId, authenticatedUser);
    }

    @Test
    void repeatedClientIdReturnsTheExistingAuthenticatedUsersTask() {
        String clientId = "legacy:local-user-1:task:task-1";
        UUID existingId = UUID.randomUUID();
        TaskEntity existing = mock(TaskEntity.class);
        when(existing.getId()).thenReturn(existingId);
        when(existing.getCategory()).thenReturn(TaskCategory.PERSONAL);
        when(existing.getPriority()).thenReturn(TaskPriority.HIGH);
        when(tasks.findByOwner_IdAndClientId(authenticatedUser, clientId))
                .thenReturn(Optional.of(existing));

        TaskDtos.TaskResponse result = service.create(new TaskDtos.TaskRequest(
                clientId, "Imported task", "", TaskCategory.PERSONAL,
                TaskPriority.HIGH, null, null, false, false, null, null, null));

        assertEquals(existingId, result.id());
        verify(users, never()).findById(authenticatedUser);
    }
}
