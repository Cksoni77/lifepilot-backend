package com.lifepilot.task;

import com.lifepilot.goal.GoalEntity;
import com.lifepilot.goal.GoalRepository;
import com.lifepilot.security.CurrentUser;
import com.lifepilot.user.AppUserRepository;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class TaskService {
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
                            && task.getDueAt().toLocalDate().isBefore(today);
                    case "today" -> !task.isCompleted() && task.getDueAt() != null
                            && task.getDueAt().toLocalDate().equals(today);
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
