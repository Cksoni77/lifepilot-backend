package com.lifepilot.task;

import com.lifepilot.goal.GoalEntity;
import com.lifepilot.goal.GoalRepository;
import com.lifepilot.security.CurrentUser;
import com.lifepilot.user.AppUserRepository;
import com.lifepilot.user.UserProfile;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class TaskService {
    private static final Map<UserProfile, Set<String>> ACTIVITY_TYPES = Map.of(
            UserProfile.WORKING_PROFESSIONAL,
            Set.of("development", "meeting", "bug_fixing", "code_review", "task"),
            UserProfile.STUDENT,
            Set.of("assignment", "examination", "study_session", "lecture", "project"),
            UserProfile.JOB_SEEKER,
            Set.of("application", "interview", "preparation", "follow_up"),
            UserProfile.ENTREPRENEUR,
            Set.of("sales", "marketing", "payment", "operations"),
            UserProfile.HABIT_BUILDER,
            Set.of("daily_habit", "weekly_habit", "custom_habit"),
            UserProfile.BUSY_INDIVIDUAL,
            Set.of("bill", "shopping", "appointment", "family_activity", "daily_activity"));

    private final TaskRepository tasks;
    private final AppUserRepository users;
    private final GoalRepository goals;
    private final CurrentUser currentUser;

    public TaskService(TaskRepository tasks, AppUserRepository users,
            GoalRepository goals, CurrentUser currentUser) {
        this.tasks = tasks;
        this.users = users;
        this.goals = goals;
        this.currentUser = currentUser;
    }

    public static boolean supportsActivityType(UserProfile profile, String activityType) {
        return activityType != null && ACTIVITY_TYPES.get(profile).contains(
                activityType.toLowerCase(java.util.Locale.ROOT));
    }

    public static TaskCategory categoryForProfile(UserProfile profile) {
        return switch (profile) {
            case WORKING_PROFESSIONAL, STUDENT, JOB_SEEKER, ENTREPRENEUR -> TaskCategory.WORK;
            case HABIT_BUILDER -> TaskCategory.HEALTH;
            case BUSY_INDIVIDUAL -> TaskCategory.PERSONAL;
        };
    }

    @Transactional(readOnly = true)
    public List<TaskDtos.TaskResponse> list(
            String search, String status, String sort,
            TaskCategory category, TaskPriority priority, Boolean completed,
            LocalDate dueFrom, LocalDate dueTo) {
        if (dueFrom != null && dueTo != null && dueTo.isBefore(dueFrom)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "dueTo must be on or after dueFrom");
        }
        if (status != null && !java.util.Set.of(
                "all", "pending", "completed", "overdue", "today")
                .contains(status.toLowerCase(java.util.Locale.ROOT))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported task status");
        }
        if (sort != null && !java.util.Set.of("createdAt", "priority", "dueDate")
                .contains(sort)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported task sort order");
        }
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        return tasks.findAllByOwner_IdOrderByCreatedAtDesc(currentUser.id()).stream()
                .filter(task -> search == null || search.isBlank()
                        || task.getTitle().toLowerCase(java.util.Locale.ROOT)
                                .contains(search.trim().toLowerCase(java.util.Locale.ROOT)))
                .filter(task -> switch (status == null ? "all" : status.toLowerCase(java.util.Locale.ROOT)) {
                    case "pending" -> !task.isCompleted();
                    case "completed" -> task.isCompleted();
                    case "overdue" -> !task.isCompleted() && task.getDueAt() != null
                            && task.getDueAt().withOffsetSameInstant(ZoneOffset.UTC)
                                    .toLocalDate().isBefore(today);
                    case "today" -> !task.isCompleted() && task.getDueAt() != null
                            && task.getDueAt().withOffsetSameInstant(ZoneOffset.UTC)
                                    .toLocalDate().equals(today);
                    default -> true;
                })
                .filter(task -> category == null || task.getCategory() == category)
                .filter(task -> priority == null || task.getPriority() == priority)
                .filter(task -> completed == null || task.isCompleted() == completed)
                .filter(task -> dueFrom == null && dueTo == null
                        || task.getDueAt() != null
                        && (dueFrom == null || !task.getDueAt().withOffsetSameInstant(ZoneOffset.UTC)
                                .toLocalDate().isBefore(dueFrom))
                        && (dueTo == null || !task.getDueAt().withOffsetSameInstant(ZoneOffset.UTC)
                                .toLocalDate().isAfter(dueTo)))
                .sorted(comparator(sort))
                .map(TaskDtos.TaskResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<TaskDtos.TaskResponse> forDateRange(
            OffsetDateTime start, OffsetDateTime end) {
        return tasks.findAllByOwner_IdAndDueAtGreaterThanEqualAndDueAtLessThanOrderByDueAtAsc(
                        currentUser.id(), start, end).stream()
                .map(TaskDtos.TaskResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<TaskDtos.TaskResponse> upcomingReminders(
            OffsetDateTime start, OffsetDateTime end) {
        return tasks
                .findAllByOwner_IdAndReminderEnabledTrueAndCompletedFalseAndReminderAtGreaterThanEqualAndReminderAtLessThanOrderByReminderAtAsc(
                        currentUser.id(), start, end).stream()
                .map(TaskDtos.TaskResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public TaskDtos.TaskResponse get(UUID id) {
        return TaskDtos.TaskResponse.from(findOwned(id));
    }

    @Transactional
    public TaskDtos.TaskResponse create(TaskDtos.TaskRequest request) {
        validateActivity(request);
        if (request.clientId() != null && !request.clientId().isBlank()) {
            var existing = tasks.findByOwner_IdAndClientId(
                    currentUser.id(), request.clientId());
            if (existing.isPresent()) return TaskDtos.TaskResponse.from(existing.get());
        }
        var owner = users.findById(currentUser.id()).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User not found"));
        TaskEntity task = new TaskEntity(owner, request);
        task.setGoal(findGoal(request.goalId()));
        return TaskDtos.TaskResponse.from(tasks.save(task));
    }

    @Transactional
    public TaskDtos.TaskResponse update(UUID id, TaskDtos.TaskRequest request) {
        validateActivity(request);
        TaskEntity task = findOwned(id);
        task.update(request);
        task.setGoal(findGoal(request.goalId()));
        return TaskDtos.TaskResponse.from(task);
    }

    @Transactional
    public void delete(UUID id) {
        tasks.delete(findOwned(id));
    }

    @Transactional
    public TaskDtos.TaskResponse complete(UUID id, boolean completed) {
        TaskEntity task = findOwned(id);
        task.setCompleted(completed);
        return TaskDtos.TaskResponse.from(task);
    }

    private TaskEntity findOwned(UUID id) {
        return tasks.findByIdAndOwner_Id(id, currentUser.id()).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "Task not found"));
    }

    private GoalEntity findGoal(UUID goalId) {
        if (goalId == null) return null;
        return goals.findByIdAndOwner_Id(goalId, currentUser.id()).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "Goal not found"));
    }

    private void validateActivity(TaskDtos.TaskRequest request) {
        TaskDtos.ActivityDetailsRequest activity = request.activityDetails();
        if (activity == null) return;
        if (activity.profile() == null || activity.activityType() == null
                || activity.fields() == null || activity.fields().size() > 64) {
            throw invalidActivity("Profile, activity type, and fields are required");
        }

        UserProfile profile;
        try {
            profile = UserProfile.valueOf(activity.profile());
        } catch (IllegalArgumentException exception) {
            throw invalidActivity("Unsupported activity profile");
        }
        String type = activity.activityType().toLowerCase(java.util.Locale.ROOT);
        if (!supportsActivityType(profile, type)) {
            throw invalidActivity("Unsupported activity type for profile");
        }

        TaskCategory expectedCategory = categoryForProfile(profile);
        if (request.category() != expectedCategory) {
            throw invalidActivity("Task category does not match the selected profile");
        }

        Map<String, Object> fields = activity.fields();
        switch (type) {
            case "meeting" -> {
                required(fields, "startAt");
                required(fields, "endAt");
                OffsetDateTime start = dateTime(fields, "startAt");
                OffsetDateTime end = dateTime(fields, "endAt");
                if (!end.isAfter(start)) throw invalidActivity("Meeting end must follow its start");
            }
            case "assignment" -> {
                required(fields, "subject");
                date(fields, "submissionDate");
                if (request.dueAt() == null) {
                    throw invalidActivity("Assignment submission deadline is required");
                }
            }
            case "interview" -> {
                dateTime(fields, "interviewDate");
                if (blank(fields.get("company")) && blank(fields.get("linkedApplication"))) {
                    throw invalidActivity("Interview requires a company or linked application");
                }
            }
            case "bill" -> {
                required(fields, "provider");
                positiveAmount(fields, "amount");
                date(fields, "billDueDate");
                if (request.dueAt() == null) {
                    throw invalidActivity("Bill due date is required");
                }
            }
            case "payment" -> {
                if (blank(fields.get("customer")) && blank(fields.get("vendor"))) {
                    throw invalidActivity("Payment requires a customer or vendor");
                }
                positiveAmount(fields, "amount");
                date(fields, "paymentDueDate");
                if (request.dueAt() == null) {
                    throw invalidActivity("Payment due date is required");
                }
            }
            case "daily_habit", "weekly_habit", "custom_habit" -> {
                required(fields, "habitName");
                required(fields, "frequency");
                required(fields, "trackingType");
                positiveNumberIfPresent(fields, "targetDuration");
                positiveIntegerIfPresent(fields, "targetRepetitions");
                if ("weekly_habit".equals(type)) {
                    Object weekdays = fields.get("selectedWeekdays");
                    if (!(weekdays instanceof List<?> days) || days.isEmpty()) {
                        throw invalidActivity("Weekly habit requires selected weekdays");
                    }
                }
            }
            case "bug_fixing" -> {
                required(fields, "project");
                required(fields, "severity");
                required(fields, "ticketReference");
            }
            case "code_review" -> {
                if (blank(fields.get("repositoryReference"))
                        && blank(fields.get("pullRequestReference"))) {
                    throw invalidActivity("Code review requires a repository or pull request reference");
                }
            }
            case "examination" -> {
                required(fields, "subject");
                required(fields, "examType");
                date(fields, "examDate");
                if (request.dueAt() == null) {
                    throw invalidActivity("Exam date is required");
                }
            }
            case "sales" -> {
                required(fields, "customer");
                positiveAmount(fields, "expectedAmount");
            }
            case "marketing" -> {
                required(fields, "campaign");
                date(fields, "targetDate");
            }
            case "operations" -> {
                required(fields, "businessMilestone");
                if (request.dueAt() == null) {
                    throw invalidActivity("Business deadline is required");
                }
            }
            case "shopping" -> required(fields, "items");
            case "appointment" -> {
                dateTime(fields, "appointmentDate");
                required(fields, "location");
            }
            case "family_activity" -> {
                required(fields, "familyMember");
                dateTime(fields, "activityDate");
            }
            default -> { }
        }

        if (request.estimatedMinutes() != null && request.estimatedMinutes() <= 0) {
            throw invalidActivity("Estimated duration must be positive");
        }
        if (fields.containsKey("startDate") && fields.containsKey("endDate")) {
            LocalDate start = date(fields, "startDate");
            LocalDate end = date(fields, "endDate");
            if (end.isBefore(start)) throw invalidActivity("End date cannot be before start date");
        }
    }

    private void required(Map<String, Object> fields, String name) {
        if (blank(fields.get(name))) throw invalidActivity(name + " is required");
    }

    private void positiveAmount(Map<String, Object> fields, String name) {
        Object value = fields.get(name);
        try {
            if (value == null || new java.math.BigDecimal(value.toString()).signum() <= 0) {
                throw invalidActivity(name + " must be greater than zero");
            }
        } catch (NumberFormatException exception) {
            throw invalidActivity(name + " must be a valid amount");
        }
    }

    private void positiveNumberIfPresent(Map<String, Object> fields, String name) {
        Object value = fields.get(name);
        if (value == null || blank(value)) return;
        try {
            if (new java.math.BigDecimal(value.toString()).signum() <= 0) {
                throw invalidActivity(name + " must be greater than zero");
            }
        } catch (NumberFormatException exception) {
            throw invalidActivity(name + " must be a valid number");
        }
    }

    private void positiveIntegerIfPresent(Map<String, Object> fields, String name) {
        Object value = fields.get(name);
        if (value == null || blank(value)) return;
        try {
            if (new java.math.BigDecimal(value.toString()).intValueExact() <= 0) {
                throw invalidActivity(name + " must be a positive whole number");
            }
        } catch (NumberFormatException | ArithmeticException exception) {
            throw invalidActivity(name + " must be a positive whole number");
        }
    }

    private OffsetDateTime dateTime(Map<String, Object> fields, String name) {
        required(fields, name);
        try {
            return OffsetDateTime.parse(fields.get(name).toString());
        } catch (java.time.format.DateTimeParseException exception) {
            throw invalidActivity(name + " must include a valid date and time");
        }
    }

    private LocalDate date(Map<String, Object> fields, String name) {
        required(fields, name);
        try {
            return LocalDate.parse(fields.get(name).toString());
        } catch (java.time.format.DateTimeParseException exception) {
            throw invalidActivity(name + " must be a valid date");
        }
    }

    private boolean blank(Object value) {
        return value == null || value.toString().isBlank();
    }

    private ResponseStatusException invalidActivity(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }

    private java.util.Comparator<TaskEntity> comparator(String sort) {
        if ("priority".equalsIgnoreCase(sort)) {
            return java.util.Comparator.comparingInt(task -> switch (task.getPriority()) {
                case HIGH -> 0;
                case MEDIUM -> 1;
                case LOW -> 2;
            });
        }
        if ("dueDate".equalsIgnoreCase(sort)) {
            return java.util.Comparator.comparing(TaskEntity::getDueAt,
                    java.util.Comparator.nullsLast(java.util.Comparator.naturalOrder()));
        }
        return java.util.Comparator.comparing(TaskEntity::getCreatedAt).reversed();
    }
}
