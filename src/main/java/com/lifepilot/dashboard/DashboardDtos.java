package com.lifepilot.dashboard;

import com.lifepilot.task.TaskDtos;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public final class DashboardDtos {
    private DashboardDtos() {}

    public record Summary(
            int totalTasks,
            int completedTasks,
            int pendingTasks,
            int overdueTasks,
            int tasksDueToday,
            double taskCompletionPercentage,
            int totalHabits,
            int completedHabitsThisWeek,
            int scheduledHabitsThisWeek,
            double averageGoalProgress,
            List<HabitProgress> habits,
            List<GoalProgress> goals,
            List<TaskDtos.TaskResponse> upcomingTasks) {}

    public record HabitProgress(
            UUID id, String name, int totalCompletions, int currentStreak, int longestStreak) {}

    public record GoalProgress(UUID id, String title, double progress, LocalDate targetDate) {}
}
