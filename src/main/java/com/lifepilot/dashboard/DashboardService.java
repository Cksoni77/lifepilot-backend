package com.lifepilot.dashboard;

import com.lifepilot.goal.GoalRepository;
import com.lifepilot.habit.HabitCompletion;
import com.lifepilot.habit.HabitCompletionRepository;
import com.lifepilot.habit.HabitEntity;
import com.lifepilot.habit.HabitRepository;
import com.lifepilot.habit.HabitStreaks;
import com.lifepilot.security.CurrentUser;
import com.lifepilot.task.TaskDtos;
import com.lifepilot.task.TaskEntity;
import com.lifepilot.task.TaskRepository;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DashboardService {
    private final TaskRepository tasks;
    private final HabitRepository habits;
    private final HabitCompletionRepository completions;
    private final GoalRepository goals;
    private final CurrentUser currentUser;

    public DashboardService(
            TaskRepository tasks,
            HabitRepository habits,
            HabitCompletionRepository completions,
            GoalRepository goals,
            CurrentUser currentUser) {
        this.tasks = tasks;
        this.habits = habits;
        this.completions = completions;
        this.goals = goals;
        this.currentUser = currentUser;
    }

    @Transactional(readOnly = true)
    public DashboardDtos.Summary summary() {
        UUID userId = currentUser.id();
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        List<TaskEntity> userTasks = tasks.findAllByOwner_IdOrderByCreatedAtDesc(userId);
        int completedTasks = (int) userTasks.stream().filter(TaskEntity::isCompleted).count();
        int overdueTasks = (int) userTasks.stream()
                .filter(task -> !task.isCompleted() && task.getDueAt() != null
                        && task.getDueAt().withOffsetSameInstant(ZoneOffset.UTC)
                                .toLocalDate().isBefore(today))
                .count();
        int tasksDueToday = (int) userTasks.stream()
                .filter(task -> !task.isCompleted() && task.getDueAt() != null
                        && task.getDueAt().withOffsetSameInstant(ZoneOffset.UTC)
                                .toLocalDate().equals(today))
                .count();
        List<TaskDtos.TaskResponse> upcoming = userTasks.stream()
                .filter(task -> !task.isCompleted() && task.getDueAt() != null
                        && task.getDueAt().isAfter(now))
                .sorted(java.util.Comparator.comparing(TaskEntity::getDueAt))
                .limit(5)
                .map(TaskDtos.TaskResponse::from)
                .toList();

        List<HabitEntity> userHabits = habits.findAllByOwner_IdOrderByCreatedAtDesc(userId);
        Map<UUID, Set<LocalDate>> completedDates = completions
                .findAllByHabit_Owner_IdOrderByCompletedOnAsc(userId).stream()
                .collect(Collectors.groupingBy(
                        completion -> completion.getHabit().getId(),
                        Collectors.mapping(HabitCompletion::getCompletedOn, Collectors.toSet())));
        LocalDate monday = today.with(DayOfWeek.MONDAY);
        int scheduledThisWeek = 0;
        int completedThisWeek = 0;
        List<DashboardDtos.HabitProgress> habitProgress = userHabits.stream().map(habit -> {
            List<LocalDate> dates = completedDates
                    .getOrDefault(habit.getId(), Set.of()).stream().sorted().toList();
            return new DashboardDtos.HabitProgress(
                    habit.getId(), habit.getName(), dates.size(),
                    HabitStreaks.current(dates, today), HabitStreaks.longest(dates));
        }).toList();
        for (HabitEntity habit : userHabits) {
            Set<LocalDate> dates = completedDates.getOrDefault(habit.getId(), Set.of());
            for (LocalDate day = monday; !day.isAfter(today); day = day.plusDays(1)) {
                if (day.isBefore(habit.getStartDate()) || !isScheduled(habit, day)) continue;
                scheduledThisWeek++;
                if (dates.contains(day)) completedThisWeek++;
            }
        }

        var userGoals = goals.findAllByOwner_IdOrderByCreatedAtDesc(userId);
        double averageGoalProgress = userGoals.isEmpty() ? 0
                : userGoals.stream().mapToDouble(goal -> goal.getProgress()).average().orElse(0);
        return new DashboardDtos.Summary(
                userTasks.size(), completedTasks, userTasks.size() - completedTasks,
                overdueTasks, tasksDueToday,
                userTasks.isEmpty() ? 0 : completedTasks * 100.0 / userTasks.size(),
                userHabits.size(), completedThisWeek, scheduledThisWeek, averageGoalProgress,
                habitProgress,
                userGoals.stream().map(goal -> new DashboardDtos.GoalProgress(
                        goal.getId(), goal.getTitle(), goal.getProgress(), goal.getTargetDate())).toList(),
                upcoming);
    }

    private boolean isScheduled(HabitEntity habit, LocalDate date) {
        return switch (habit.getFrequency().toLowerCase(java.util.Locale.ROOT)) {
            case "weekdays" -> date.getDayOfWeek().getValue() <= 5;
            case "weekends" -> date.getDayOfWeek().getValue() >= 6;
            case "customdays" -> habit.getSelectedWeekdays().contains(date.getDayOfWeek().getValue());
            default -> true;
        };
    }
}
