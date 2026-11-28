package com.lifepilot.ai;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lifepilot.common.SafeApiException;
import com.lifepilot.calendar.CalendarEventRepository;
import com.lifepilot.goal.GoalRepository;
import com.lifepilot.habit.HabitCompletionRepository;
import com.lifepilot.habit.HabitRepository;
import com.lifepilot.security.CurrentUser;
import com.lifepilot.task.TaskDtos;
import com.lifepilot.task.TaskRepository;
import com.lifepilot.task.TaskService;
import com.lifepilot.user.AppUserRepository;
import com.lifepilot.user.UserPreferencesDtos;
import com.lifepilot.user.UserPreferencesService;
import com.lifepilot.user.UserProfile;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.server.ResponseStatusException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Service
public class AiService {
    private static final Logger logger = LoggerFactory.getLogger(AiService.class);

    private final TaskRepository tasks;
    private final HabitRepository habits;
    private final HabitCompletionRepository completions;
    private final GoalRepository goals;
    private final CalendarEventRepository calendarEvents;
    private final CurrentUser currentUser;
    private final AppUserRepository users;
    private final AiConversationRepository conversations;
    private final AiMessageRepository messages;
    private final RecommendationEngine recommendations;
    private final OllamaClient ollama;
    private final ObjectMapper objectMapper;
    private final UserPreferencesService preferences;

    public AiService(TaskRepository tasks, HabitRepository habits, GoalRepository goals,
            HabitCompletionRepository completions, CalendarEventRepository calendarEvents,
            CurrentUser currentUser, AppUserRepository users,
            AiConversationRepository conversations, AiMessageRepository messages,
            RecommendationEngine recommendations,
            OllamaClient ollama, ObjectMapper objectMapper,
            UserPreferencesService preferences) {
        this.tasks = tasks;
        this.habits = habits;
        this.completions = completions;
        this.calendarEvents = calendarEvents;
        this.goals = goals;
        this.currentUser = currentUser;
        this.users = users;
        this.conversations = conversations;
        this.messages = messages;
        this.recommendations = recommendations;
        this.ollama = ollama;
        this.objectMapper = objectMapper;
        this.preferences = preferences;
    }

    @Transactional
    public AiDtos.ChatResponse chat(AiDtos.ChatRequest request) {
        UUID userId = currentUser.id();
        if (!ollama.isEnabled()) {
            throw new SafeApiException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "Local AI is disabled on this server.");
        }
        AiConversationEntity conversation = request.conversationId() == null
                ? newConversation(userId, request.message())
                : findOwnedConversation(request.conversationId());
        List<AiMessageEntity> history = new ArrayList<>(
                messages.findTop10ByConversation_IdOrderByCreatedAtDesc(conversation.getId()));
        Collections.reverse(history);
        List<TaskDtos.TaskResponse> userTasks = taskResponses(userId);
        List<UserProfile> profiles = preferences.getProfiles();
        List<RecommendationEngine.ScoredTask> ranked = recommendations.rank(userTasks, profiles);
        String context = context(userId, userTasks, history, profiles);
        messages.save(new AiMessageEntity(conversation, "user", request.message()));
        conversation.markActivity();
        AiDtos.ChatResponse answer;
        long startedAt = System.nanoTime();
        try {
            answer = parseResponse(ollama.respond(request.message(), context), userTasks);
        } catch (ResourceAccessException exception) {
            logger.warn("Ollama chat request failed after {} ms: connection unavailable",
                    (System.nanoTime() - startedAt) / 1_000_000);
            throw new SafeApiException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "Could not reach local Ollama. Start Ollama and verify OLLAMA_BASE_URL.");
        } catch (RestClientResponseException exception) {
            logger.warn("Ollama chat request returned {} after {} ms",
                    exception.getStatusCode().value(),
                    (System.nanoTime() - startedAt) / 1_000_000);
            throw new SafeApiException(
                    HttpStatus.BAD_GATEWAY,
                    "Ollama could not process the chat request.");
        } catch (RestClientException | JsonProcessingException
                | IllegalArgumentException | IllegalStateException exception) {
            logger.warn("Ollama chat response unavailable or invalid after {} ms ({})",
                    (System.nanoTime() - startedAt) / 1_000_000,
                    exception.getClass().getSimpleName());
            throw new SafeApiException(
                    HttpStatus.BAD_GATEWAY,
                    "Ollama returned an invalid chat response.");
        }
        messages.save(new AiMessageEntity(conversation, "assistant", answer.message()));
        conversation.markActivity();
        return new AiDtos.ChatResponse(answer.message(), answer.intent(), answer.relevantTaskIds(),
                answer.recommendations(), answer.suggestedActions(), answer.explanation(),
                answer.requiresConfirmation(), answer.providerAvailable(), conversation.getId());
    }

    @Transactional(readOnly = true)
    public AiDtos.QuickAddResponse quickAdd(AiDtos.QuickAddRequest request) {
        if (!ollama.isEnabled()) {
            throw new SafeApiException(
                    HttpStatus.SERVICE_UNAVAILABLE, "Local AI is disabled on this server.");
        }

        UserPreferencesDtos.PreferencesResponse userPreferences = preferences.get();
        ZoneId zoneId;
        try {
            zoneId = ZoneId.of(userPreferences.timezone());
        } catch (DateTimeException exception) {
            throw new ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR, "The saved timezone is invalid");
        }
        List<UserProfile> selectedProfiles = preferences.getProfiles();
        List<String> profileValues = (selectedProfiles.isEmpty()
                        ? List.of(UserProfile.values())
                        : selectedProfiles).stream()
                .map(Enum::name)
                .toList();

        String rawResponse;
        try {
            rawResponse = ollama.quickAdd(
                    request.text(), LocalDate.now(zoneId), zoneId, profileValues);
        } catch (ResourceAccessException exception) {
            throw new SafeApiException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "Could not reach local Ollama. Start Ollama and verify OLLAMA_BASE_URL.");
        } catch (RestClientResponseException exception) {
            if (exception.getStatusCode().value() == 404) {
                throw new SafeApiException(
                        HttpStatus.SERVICE_UNAVAILABLE,
                        "The configured Ollama model is unavailable. Pull the configured model first.");
            }
            throw new SafeApiException(
                    HttpStatus.BAD_GATEWAY, "Ollama could not process the quick-add request.");
        } catch (IllegalStateException exception) {
            throw new SafeApiException(
                    HttpStatus.BAD_GATEWAY, "Ollama returned an empty or invalid response.");
        }

        AiDtos.QuickAddModelResponse parsed;
        try {
            parsed = objectMapper.readValue(rawResponse, AiDtos.QuickAddModelResponse.class);
        } catch (JsonProcessingException exception) {
            throw new SafeApiException(
                    HttpStatus.BAD_GATEWAY, "Ollama returned invalid structured activity data.");
        }
        if (parsed == null || parsed.activities() == null || parsed.activities().size() > 20) {
            throw new SafeApiException(
                    HttpStatus.BAD_GATEWAY, "Ollama returned an invalid activity list.");
        }
        if (parsed.requiresClarification()
                && (parsed.clarificationQuestion() == null
                        || parsed.clarificationQuestion().isBlank())) {
            throw new SafeApiException(
                    HttpStatus.BAD_GATEWAY, "Ollama did not provide the requested clarification.");
        }
        if (!parsed.requiresClarification() && parsed.activities().isEmpty()) {
            throw new SafeApiException(
                    HttpStatus.BAD_GATEWAY, "Ollama did not identify any activities.");
        }

        List<AiDtos.ActivitySuggestion> suggestions = new ArrayList<>();
        for (int index = 0; index < parsed.activities().size(); index++) {
            suggestions.add(validateSuggestion(
                    parsed.activities().get(index), request.requestId(), index, selectedProfiles));
        }
        boolean requiresClarification = parsed.requiresClarification();
        String clarificationQuestion = parsed.clarificationQuestion();
        boolean meetingNeedsTime = suggestions.stream().anyMatch(suggestion ->
                suggestion.activityType().equals("meeting")
                        && (suggestion.startAt() == null
                                || !suggestion.fields().containsKey("endAt")));
        boolean reminderNeedsTime = suggestions.stream().anyMatch(suggestion ->
                suggestion.reminderSuggested() && suggestion.reminderAt() == null);
        if ((meetingNeedsTime || reminderNeedsTime) && !requiresClarification) {
            requiresClarification = true;
            clarificationQuestion = meetingNeedsTime
                    ? "What start and end time should the meeting use?"
                    : "What time would you like the reminder?";
        }
        return new AiDtos.QuickAddResponse(
                request.requestId(), List.copyOf(suggestions),
                requiresClarification, clarificationQuestion);
    }

    private AiDtos.ActivitySuggestion validateSuggestion(
            AiDtos.ActivitySuggestionInput suggestion,
            UUID requestId,
            int index,
            List<UserProfile> selectedProfiles) {
        if (suggestion == null || suggestion.title() == null || suggestion.title().isBlank()
                || suggestion.title().length() > 200
                || suggestion.description() == null || suggestion.description().length() > 4000
                || suggestion.profile() == null || suggestion.category() == null
                || suggestion.priority() == null || suggestion.activityType() == null
                || suggestion.activityType().length() > 40
                || !TaskService.supportsActivityType(
                        suggestion.profile(), suggestion.activityType())
                || TaskService.categoryForProfile(suggestion.profile()) != suggestion.category()
                || (!selectedProfiles.isEmpty()
                        && !selectedProfiles.contains(suggestion.profile()))
                || (suggestion.estimatedMinutes() != null
                        && (suggestion.estimatedMinutes() <= 0
                                || suggestion.estimatedMinutes() > 1440))
                || !Double.isFinite(suggestion.confidence())
                || suggestion.confidence() < 0 || suggestion.confidence() > 1
                || suggestion.fields() == null || suggestion.fields().size() > 64) {
            throw new SafeApiException(
                    HttpStatus.BAD_GATEWAY, "Ollama returned an invalid activity suggestion.");
        }
        Map<String, Object> fields = new java.util.HashMap<>(suggestion.fields());
        fields.replaceAll((key, value) -> {
            if (key == null || key.isBlank() || key.length() > 80) {
                throw new SafeApiException(
                        HttpStatus.BAD_GATEWAY, "Ollama returned invalid activity fields.");
            }
            return value;
        });
        fields.values().removeIf(java.util.Objects::isNull);
        if (suggestion.startAt() != null) {
            fields.put("startAt", suggestion.startAt().toString());
        }
        if (suggestion.reminderAt() != null) {
            fields.put("reminderAt", suggestion.reminderAt().toString());
        }
        return new AiDtos.ActivitySuggestion(
                requestId + "-" + index,
                suggestion.title().trim(),
                suggestion.description(),
                suggestion.profile(),
                suggestion.category(),
                suggestion.activityType(),
                suggestion.priority(),
                suggestion.dueDate(),
                suggestion.startAt(),
                suggestion.reminderAt(),
                suggestion.estimatedMinutes(),
                suggestion.reminderSuggested(),
                suggestion.confidence(),
                java.util.Collections.unmodifiableMap(fields));
    }

    @Transactional(readOnly = true)
    public List<AiDtos.ConversationResponse> conversations() {
        return conversations.findAllByOwner_IdOrderByUpdatedAtDesc(currentUser.id()).stream()
                .map(conversation -> new AiDtos.ConversationResponse(
                        conversation.getId(), conversation.getTitle(), conversation.getUpdatedAt()))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<AiDtos.MessageResponse> messages(UUID conversationId) {
        findOwnedConversation(conversationId);
        return messages.findAllByConversation_IdOrderByCreatedAtAsc(conversationId).stream()
                .map(message -> new AiDtos.MessageResponse(message.getId(), message.getRole(),
                        message.getContent(), message.getCreatedAt()))
                .toList();
    }

    @Transactional(readOnly = true)
    public AiDtos.DailyPlanResponse dailyPlan() {
        UUID userId = currentUser.id();
        List<UserProfile> profiles = preferences.getProfiles();
        List<RecommendationEngine.ScoredTask> ranked =
                recommendations.rank(taskResponses(userId), profiles);
        return new AiDtos.DailyPlanResponse(ranked, List.of(),
                List.of("What hours are you available, and what fixed commitments should the plan avoid?"),
                "Tasks are ranked from saved priorities, deadlines, and estimated durations. "
                        + "No time slots were assigned because availability and calendar commitments are not configured.");
    }

    private List<TaskDtos.TaskResponse> taskResponses(UUID userId) {
        return tasks.findAllByOwner_IdOrderByCreatedAtDesc(userId).stream()
                .limit(100)
                .map(TaskDtos.TaskResponse::from).toList();
    }

    private AiConversationEntity newConversation(UUID userId, String message) {
        var owner = users.findById(userId).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User not found"));
        return conversations.save(new AiConversationEntity(owner, message));
    }

    private AiConversationEntity findOwnedConversation(UUID conversationId) {
        return conversations.findByIdAndOwner_Id(conversationId, currentUser.id())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "AI conversation not found"));
    }

    private String context(
            UUID userId, List<TaskDtos.TaskResponse> userTasks,
            List<AiMessageEntity> history, List<UserProfile> profiles) {
        var userHabits = habits.findAllByOwner_IdOrderByCreatedAtDesc(userId).stream()
                .limit(50)
                .map(habit -> {
                    var recentDates = completions
                            .findTop30ByHabit_IdOrderByCompletedOnDesc(habit.getId()).stream()
                            .sorted(java.util.Comparator.comparing(
                                    value -> value.getCompletedOn()))
                            .map(value -> value.getCompletedOn().toString()).toList();
                    return java.util.Map.of(
                            "name", habit.getName(),
                            "frequency", habit.getFrequency(),
                            "startDate", habit.getStartDate().toString(),
                            "completionDates", recentDates);
                })
                .toList();
        var userGoals = goals.findAllByOwner_IdOrderByCreatedAtDesc(userId).stream().limit(50)
                .map(goal -> java.util.Map.of(
                        "title", goal.getTitle(),
                        "progress", goal.getProgress(),
                        "targetDate", goal.getTargetDate() == null ? "" : goal.getTargetDate().toString()))
                .toList();
        var upcomingEvents = calendarEvents.findTop50ByOwner_IdAndEndsAtAfterOrderByStartsAtAsc(
                        userId, OffsetDateTime.now(ZoneOffset.UTC)).stream()
                .map(event -> java.util.Map.of(
                        "title", event.getTitle(),
                        "startsAt", event.getStartsAt().toString(),
                        "endsAt", event.getEndsAt().toString(),
                        "fixed", event.isFixed()))
                .toList();
        try {
            return objectMapper.writeValueAsString(java.util.Map.of(
                    "todayUtc", LocalDate.now(ZoneOffset.UTC).toString(),
                    "selectedProfiles", profiles.stream().map(Enum::name).toList(),
                    "tasks", userTasks.stream().limit(50)
                            .map(task -> java.util.Map.of(
                                    "id", task.id(),
                                    "title", task.title(),
                                    "description", truncate(task.description(), 300),
                                    "priority", task.priority(),
                                    "completed", task.completed(),
                                    "dueDate", task.dueDate() == null ? "" : task.dueDate().toString(),
                                    "estimatedMinutes", task.estimatedMinutes() == null
                                            ? "" : task.estimatedMinutes(),
                                    "goalId", task.goalId() == null ? "" : task.goalId()))
                            .toList(),
                    "habits", userHabits,
                    "goals", userGoals,
                    "calendarEvents", upcomingEvents,
                    "conversationHistory", history.stream().map(message -> java.util.Map.of(
                            "role", message.getRole(),
                            "content", truncate(message.getContent(), 1500))).toList()));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Could not serialize user context", exception);
        }
    }

    private AiDtos.ChatResponse deterministicReply(
            String message, List<TaskDtos.TaskResponse> userTasks,
            List<RecommendationEngine.ScoredTask> ranked, List<UserProfile> profiles) {
        String normalized = message.toLowerCase(java.util.Locale.ROOT);
        if (normalized.contains("today")) {
            LocalDate today = LocalDate.now(ZoneOffset.UTC);
            List<TaskDtos.TaskResponse> dueToday = userTasks.stream()
                    .filter(task -> !task.completed() && task.dueDate() != null
                            && task.dueDate().withOffsetSameInstant(ZoneOffset.UTC)
                                    .toLocalDate().equals(today)).toList();
            return new AiDtos.ChatResponse(
                    dueToday.isEmpty() ? "You have no saved pending tasks due today."
                            : "Your pending tasks due today: " + titles(dueToday),
                    "tasks_today", dueToday.stream().map(TaskDtos.TaskResponse::id).toList(),
                    List.of(), List.of(), "Based on your saved task due dates.",
                    false, false, null);
        }
        if (normalized.contains("overdue")) {
            LocalDate today = LocalDate.now(ZoneOffset.UTC);
            List<TaskDtos.TaskResponse> overdue = userTasks.stream()
                    .filter(task -> !task.completed() && task.dueDate() != null
                            && task.dueDate().withOffsetSameInstant(ZoneOffset.UTC)
                                    .toLocalDate().isBefore(today)).toList();
            return new AiDtos.ChatResponse(
                    overdue.isEmpty() ? "You have no saved overdue tasks."
                            : "Your overdue tasks: " + titles(overdue),
                    "overdue_tasks", overdue.stream().map(TaskDtos.TaskResponse::id).toList(),
                    List.of(), List.of(), "Based on your saved task due dates.",
                    false, false, null);
        }
        if (normalized.contains("priorit")) {
            List<String> lines = ranked.stream().limit(5)
                    .map(task -> task.task().title() + ": " + task.explanation()).toList();
            String profileFocus = profiles.isEmpty() ? ""
                    : " Profile focus: " + String.join(", ",
                            profiles.stream().map(profile -> profile.name().toLowerCase(
                                    java.util.Locale.ROOT).replace('_', ' ')).toList())
                            + ". Recommendations use only your saved tasks.";
            return new AiDtos.ChatResponse(
                    lines.isEmpty() ? "You have no pending tasks to prioritize."
                            : "Recommended order: " + String.join(" ", lines) + profileFocus,
                    "prioritize_tasks", ranked.stream().limit(5)
                            .map(task -> task.task().id()).toList(),
                    lines, List.of(), "Sorted by saved priority and due dates.",
                    false, false, null);
        }
        return new AiDtos.ChatResponse(
                "AI chat is not configured. I can still answer questions about your saved tasks "
                        + "for today, overdue tasks, or task priorities. Start local Ollama to enable natural-language responses.",
                "unsupported_without_provider", List.of(), List.of(), List.of(),
                "No AI provider is configured; no external request was made.",
                false, false, null);
    }

    private AiDtos.ChatResponse parseResponse(
            String response, List<TaskDtos.TaskResponse> userTasks)
            throws JsonProcessingException {
        AiDtos.ChatResponse parsed = objectMapper.readValue(response, AiDtos.ChatResponse.class);
        if (parsed.message() == null || parsed.message().isBlank() || parsed.message().length() > 4000) {
            throw new IllegalArgumentException("AI response message is missing or too long");
        }
        java.util.Set<UUID> ownedTaskIds = userTasks.stream()
                .map(TaskDtos.TaskResponse::id).collect(java.util.stream.Collectors.toSet());
        List<UUID> validTaskIds = parsed.relevantTaskIds() == null ? List.of()
                : parsed.relevantTaskIds().stream().filter(ownedTaskIds::contains).distinct().toList();
        List<String> actions = parsed.suggestedActions() == null ? List.of()
                : parsed.suggestedActions().stream().filter(value -> value != null && !value.isBlank())
                        .limit(10).toList();
        return new AiDtos.ChatResponse(parsed.message(), parsed.intent(),
                validTaskIds,
                parsed.recommendations() == null ? List.of()
                        : parsed.recommendations().stream().filter(value -> value != null && !value.isBlank())
                                .limit(10).toList(),
                actions,
                parsed.explanation(),
                parsed.requiresConfirmation() || !actions.isEmpty(), true,
                parsed.conversationId());
    }

    private String truncate(String text, int maxLength) {
        return text.length() <= maxLength ? text : text.substring(0, maxLength);
    }

    private String titles(List<TaskDtos.TaskResponse> tasks) {
        return String.join(", ", tasks.stream().map(TaskDtos.TaskResponse::title).toList());
    }
}
