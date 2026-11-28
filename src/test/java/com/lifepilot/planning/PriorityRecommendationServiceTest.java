package com.lifepilot.planning;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.lifepilot.TestAppProperties;
import com.lifepilot.security.CurrentUser;
import com.lifepilot.task.TaskEntity;
import com.lifepilot.task.TaskPriority;
import com.lifepilot.task.TaskRepository;
import java.util.Optional;
import java.util.UUID;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

class PriorityRecommendationServiceTest {
    private final TaskRepository tasks = mock(TaskRepository.class);
    private final CurrentUser currentUser = mock(CurrentUser.class);
    private final UUID userId = UUID.randomUUID();
    private final PriorityRecommendationService service =
            new PriorityRecommendationService(tasks, currentUser, TestAppProperties.defaults());

    @Test
    void producesWeightedProposalWithoutChangingManualPriority() {
        UUID taskId = UUID.randomUUID();
        TaskEntity task = mock(TaskEntity.class);
        when(currentUser.id()).thenReturn(userId);
        when(tasks.findByIdAndOwner_Id(taskId, userId)).thenReturn(Optional.of(task));
        when(task.getId()).thenReturn(taskId);
        when(task.getTitle()).thenReturn("Prepare interview");
        when(task.getPriority()).thenReturn(TaskPriority.LOW);

        PlanningDtos.PriorityResponse result = service.recommend(
                new PlanningDtos.PriorityRequest(taskId, 5, 5, 5));

        assertEquals(TaskPriority.LOW, result.currentPriority());
        assertEquals(TaskPriority.HIGH, result.recommendedPriority());
        assertEquals(75.0, result.score());
        verify(task).getPriority();
    }

    @Test
    void doesNotRevealTasksOwnedByAnotherUser() {
        UUID taskId = UUID.randomUUID();
        when(currentUser.id()).thenReturn(userId);
        when(tasks.findByIdAndOwner_Id(taskId, userId)).thenReturn(Optional.empty());

        assertThrows(ResponseStatusException.class, () -> service.recommend(
                new PlanningDtos.PriorityRequest(taskId, 3, 3, 3)));
    }

    @Test
    void evaluatesDeadlinesInTheirStoredTimezone() {
        UUID taskId = UUID.randomUUID();
        TaskEntity task = mock(TaskEntity.class);
        ZoneOffset offset = ZoneOffset.ofHours(14);
        OffsetDateTime dueAt = LocalDate.now(offset).atStartOfDay().atOffset(offset);
        when(currentUser.id()).thenReturn(userId);
        when(tasks.findByIdAndOwner_Id(taskId, userId)).thenReturn(Optional.of(task));
        when(task.getId()).thenReturn(taskId);
        when(task.getTitle()).thenReturn("Local midnight deadline");
        when(task.getPriority()).thenReturn(TaskPriority.MEDIUM);
        when(task.getDueAt()).thenReturn(dueAt);

        PlanningDtos.PriorityResponse result = service.recommend(
                new PlanningDtos.PriorityRequest(taskId, 1, 1, 1));

        assertEquals("HIGH", result.deadlineRisk());
    }
}
