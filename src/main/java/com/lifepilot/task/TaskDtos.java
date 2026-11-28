package com.lifepilot.task;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

public final class TaskDtos {
    private TaskDtos() {}

    public record TaskRequest(
            @Size(max = 80) String clientId,
            @NotBlank @Size(max = 200) String title,
            @Size(max = 4000) String description,
            @NotNull TaskCategory category,
            @NotNull TaskPriority priority,
            OffsetDateTime dueAt,
            OffsetDateTime reminderAt,
            boolean reminderEnabled,
            Boolean completed,
            OffsetDateTime completedAt,
            @Positive Integer estimatedMinutes,
            UUID goalId,
            ActivityDetailsRequest activityDetails) {}

    public record ActivityDetailsRequest(
            String profile, String activityType, Map<String, Object> fields) {}

    public record TaskResponse(
            UUID id, UUID goalId, String title, String description, String category, String priority,
            boolean completed, OffsetDateTime dueDate, OffsetDateTime reminderDate,
            boolean reminderEnabled, Integer estimatedMinutes, OffsetDateTime createdAt,
            OffsetDateTime completedAt, ActivityDetailsResponse activityDetails) {
        public static TaskResponse from(TaskEntity task) {
            return new TaskResponse(task.getId(),
                    task.getGoal() == null ? null : task.getGoal().getId(),
                    task.getTitle(), task.getDescription(),
                    task.getCategory().name().toLowerCase(java.util.Locale.ROOT),
                    task.getPriority().name().toLowerCase(java.util.Locale.ROOT),
                    task.isCompleted(), task.getDueAt(), task.getReminderAt(),
                    task.isReminderEnabled(), task.getEstimatedMinutes(),
                    task.getCreatedAt(), task.getCompletedAt(),
                    task.getActivityDetails() == null
                            ? null : ActivityDetailsResponse.from(task.getActivityDetails()));
        }
    }

    public record ActivityDetailsResponse(
            String profile, String activityType, Map<String, Object> fields) {
        static ActivityDetailsResponse from(TaskActivityDetails details) {
            return new ActivityDetailsResponse(
                    details.getProfile(), details.getActivityType(), details.getFields());
        }
    }
}
