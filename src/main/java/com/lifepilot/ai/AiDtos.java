package com.lifepilot.ai;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.NotNull;
import com.lifepilot.task.TaskCategory;
import com.lifepilot.task.TaskPriority;
import com.lifepilot.user.UserProfile;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class AiDtos {
    private AiDtos() {}

    public record ChatRequest(
            @NotBlank @Size(max = 4000) String message,
            UUID conversationId) {}

    public record QuickAddRequest(
            @NotBlank @Size(max = 4000) String text,
            @NotNull UUID requestId) {}

    public record QuickAddResponse(
            UUID requestId,
            List<ActivitySuggestion> activities,
            boolean requiresClarification,
            String clarificationQuestion) {}

    public record ActivitySuggestion(
            String clientId,
            String title,
            String description,
            UserProfile profile,
            TaskCategory category,
            String activityType,
            TaskPriority priority,
            LocalDate dueDate,
            OffsetDateTime startAt,
            OffsetDateTime reminderAt,
            Integer estimatedMinutes,
            boolean reminderSuggested,
            double confidence,
            Map<String, Object> fields) {}

    public record QuickAddModelResponse(
            List<ActivitySuggestionInput> activities,
            boolean requiresClarification,
            String clarificationQuestion) {}

    public record ActivitySuggestionInput(
            String title,
            String description,
            UserProfile profile,
            TaskCategory category,
            String activityType,
            TaskPriority priority,
            LocalDate dueDate,
            OffsetDateTime startAt,
            OffsetDateTime reminderAt,
            Integer estimatedMinutes,
            boolean reminderSuggested,
            double confidence,
            Map<String, Object> fields) {}

    public record ChatResponse(
            String message,
            String intent,
            List<UUID> relevantTaskIds,
            List<String> recommendations,
            List<String> suggestedActions,
            String explanation,
            boolean requiresConfirmation,
            boolean providerAvailable,
            UUID conversationId) {}

    public record ConversationResponse(
            UUID id, String title, java.time.OffsetDateTime updatedAt) {}

    public record MessageResponse(
            UUID id, String role, String content, java.time.OffsetDateTime createdAt) {}

    public record DailyPlanResponse(
            List<RecommendationEngine.ScoredTask> rankedTasks,
            List<String> scheduledItems,
            List<String> questions,
            String explanation) {}
}
