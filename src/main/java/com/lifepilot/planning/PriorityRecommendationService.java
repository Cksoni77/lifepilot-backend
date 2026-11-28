package com.lifepilot.planning;

import com.lifepilot.config.AppProperties;
import com.lifepilot.security.CurrentUser;
import com.lifepilot.task.TaskEntity;
import com.lifepilot.task.TaskPriority;
import com.lifepilot.task.TaskRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class PriorityRecommendationService {
    private final TaskRepository tasks;
    private final CurrentUser currentUser;
    private final AppProperties.PriorityScoring weights;

    public PriorityRecommendationService(
            TaskRepository tasks, CurrentUser currentUser, AppProperties properties) {
        this.tasks = tasks;
        this.currentUser = currentUser;
        this.weights = properties.priorityScoring();
    }

    @Transactional(readOnly = true)
    public PlanningDtos.PriorityResponse recommend(PlanningDtos.PriorityRequest request) {
        TaskEntity task = tasks.findByIdAndOwner_Id(request.taskId(), currentUser.id())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Task not found"));
        double urgency = scale(request.urgency());
        double importance = scale(request.importance());
        double consequence = scale(request.consequenceOfDelay());
        LocalDate dueDate = task.getDueAt() == null
                ? null : task.getDueAt().toLocalDate();
        LocalDate today = task.getDueAt() == null
                ? LocalDate.now(ZoneOffset.UTC)
                : LocalDate.now(task.getDueAt().getOffset());
        double deadline = deadlineScore(dueDate, today);
        double score = weightedScore(urgency, importance, deadline, consequence);
        TaskPriority recommendation = score >= 70 ? TaskPriority.HIGH
                : score >= 40 ? TaskPriority.MEDIUM : TaskPriority.LOW;

        String risk = deadlineRisk(dueDate, today);
        String reason = "Urgency " + request.urgency() + "/5, importance "
                + request.importance() + "/5, deadline risk " + risk.toLowerCase()
                + ", and consequence of delay " + request.consequenceOfDelay() + "/5.";
        String nextAction = recommendation == TaskPriority.HIGH
                ? "Start this task as soon as practical."
                : recommendation == TaskPriority.MEDIUM
                        ? "Schedule a focused time to work on this task."
                        : "Keep this task in your planned task list.";
        return new PlanningDtos.PriorityResponse(
                task.getId(), task.getTitle(), task.getPriority(), recommendation,
                score, reason, risk, nextAction);
    }

    private double weightedScore(
            double urgency, double importance, double deadline, double consequence) {
        BigDecimal score = weights.urgencyWeight().multiply(BigDecimal.valueOf(urgency))
                .add(weights.importanceWeight().multiply(BigDecimal.valueOf(importance)))
                .add(weights.deadlineWeight().multiply(BigDecimal.valueOf(deadline)))
                .add(weights.consequenceWeight().multiply(BigDecimal.valueOf(consequence)));
        return score.setScale(2, RoundingMode.HALF_UP).doubleValue();
    }

    private double deadlineScore(LocalDate dueDate, LocalDate today) {
        if (dueDate == null) return 0;
        long days = ChronoUnit.DAYS.between(today, dueDate);
        if (days <= 0) return 100;
        if (days <= 2) return 85;
        if (days <= 7) return 65;
        if (days <= 30) return 40;
        return 20;
    }

    private String deadlineRisk(LocalDate dueDate, LocalDate today) {
        if (dueDate == null) return "UNKNOWN";
        long days = ChronoUnit.DAYS.between(today, dueDate);
        if (days < 0) return "OVERDUE";
        if (days <= 2) return "HIGH";
        if (days <= 7) return "MEDIUM";
        return "LOW";
    }

    private double scale(int value) {
        return (value - 1) * 25.0;
    }
}
