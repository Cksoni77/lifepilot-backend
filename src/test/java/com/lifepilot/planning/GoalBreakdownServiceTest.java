package com.lifepilot.planning;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lifepilot.ai.OllamaClient;
import com.lifepilot.common.SafeApiException;
import com.lifepilot.task.TaskCategory;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.ResourceAccessException;

class GoalBreakdownServiceTest {
    private final OllamaClient ollama = mock(OllamaClient.class);
    private final GoalBreakdownService service =
            new GoalBreakdownService(ollama, new ObjectMapper());

    GoalBreakdownServiceTest() {
        when(ollama.isEnabled()).thenReturn(true);
    }

    @Test
    void returnsValidatedUnpersistedProposal() throws Exception {
        when(ollama.breakDownGoal("Learn Spring", "", "WORK", null))
                .thenReturn("""
                        {"activities":[{"title":"Review Spring fundamentals",
                        "description":"Study core concepts","category":"WORK","priority":"MEDIUM",
                        "estimatedMinutes":45,"suggestedDueDate":null,"dependsOnIndices":[],
                        "milestone":"Foundations","completionCriteria":"Summarize key concepts"}]}""");

        PlanningDtos.GoalBreakdownResponse response = service.breakDown(
                new PlanningDtos.GoalBreakdownRequest(
                        "Learn Spring", null, TaskCategory.WORK, null));

        assertEquals(1, response.activities().size());
        assertEquals("Foundations", response.activities().get(0).milestone());
    }

    @Test
    void rejectsDueDateWhenNoTargetDateWasProvided() throws Exception {
        when(ollama.breakDownGoal("Learn Spring", "", "WORK", null))
                .thenReturn("""
                        {"activities":[{"title":"Study","description":"","category":"WORK",
                        "priority":"MEDIUM","estimatedMinutes":30,"suggestedDueDate":"2030-01-02",
                        "dependsOnIndices":[],"milestone":null,
                        "completionCriteria":"Complete a summary"}]}""");

        assertThrows(SafeApiException.class, () -> service.breakDown(
                new PlanningDtos.GoalBreakdownRequest(
                        "Learn Spring", null, TaskCategory.WORK, null)));
    }

    @Test
    void rejectsDependenciesOnCurrentOrLaterActivities() throws Exception {
        when(ollama.breakDownGoal("Learn Spring", "", "WORK", null))
                .thenReturn("""
                        {"activities":[{"title":"Study","description":"","category":"WORK",
                        "priority":"MEDIUM","estimatedMinutes":30,"suggestedDueDate":null,
                        "dependsOnIndices":[0],"milestone":null,
                        "completionCriteria":"Complete a summary"}]}""");

        assertThrows(SafeApiException.class, () -> service.breakDown(
                new PlanningDtos.GoalBreakdownRequest(
                        "Learn Spring", null, TaskCategory.WORK, null)));
    }

    @Test
    void reportsUnavailableOllamaWithoutFallback() {
        when(ollama.isEnabled()).thenReturn(false);
        assertThrows(SafeApiException.class, () -> service.breakDown(
                new PlanningDtos.GoalBreakdownRequest(
                        "Learn Spring", null, TaskCategory.WORK, null)));
    }

    @Test
    void reportsOllamaConnectionFailure() {
        when(ollama.breakDownGoal("Learn Spring", "", "WORK", null))
                .thenThrow(new ResourceAccessException("connection refused"));
        assertThrows(SafeApiException.class, () -> service.breakDown(
                new PlanningDtos.GoalBreakdownRequest(
                        "Learn Spring", null, TaskCategory.WORK, null)));
    }

    @Test
    void rejectsMalformedAiJson() {
        when(ollama.breakDownGoal("Learn Spring", "", "WORK", null))
                .thenReturn("{not-json");
        assertThrows(SafeApiException.class, () -> service.breakDown(
                new PlanningDtos.GoalBreakdownRequest(
                        "Learn Spring", null, TaskCategory.WORK, null)));
    }
}
