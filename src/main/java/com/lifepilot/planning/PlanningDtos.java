package com.lifepilot.planning;

import com.lifepilot.task.TaskCategory;
import com.lifepilot.task.TaskPriority;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public final class PlanningDtos {
    private PlanningDtos() {}

    public record PriorityRequest(
            @NotNull UUID taskId,
            @Min(1) @Max(5) int urgency,
            @Min(1) @Max(5) int importance,
            @Min(1) @Max(5) int consequenceOfDelay) {}

    public record PriorityResponse(
            UUID taskId,
            String taskTitle,
            TaskPriority currentPriority,
            TaskPriority recommendedPriority,
            double score,
            String reason,
            String deadlineRisk,
            String nextAction) {}

    public record GoalBreakdownRequest(
            @NotBlank @Size(max = 200) String title,
            @Size(max = 2000) String description,
            @NotNull TaskCategory category,
            LocalDate targetDate) {}

    public record GoalBreakdownResponse(List<GoalActivitySuggestion> activities) {}

    public record GoalActivitySuggestion(
            String title,
            String description,
            TaskCategory category,
            TaskPriority priority,
            Integer estimatedMinutes,
            LocalDate suggestedDueDate,
            List<Integer> dependsOnIndices,
            String milestone,
            String completionCriteria) {}
}
