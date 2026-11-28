package com.lifepilot.planning;

import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/planning")
public class PlanningController {
    private final PriorityRecommendationService priorities;
    private final GoalBreakdownService goalBreakdown;

    public PlanningController(
            PriorityRecommendationService priorities, GoalBreakdownService goalBreakdown) {
        this.priorities = priorities;
        this.goalBreakdown = goalBreakdown;
    }

    @PostMapping("/priority/recommend")
    public PlanningDtos.PriorityResponse recommendPriority(
            @Valid @RequestBody PlanningDtos.PriorityRequest request) {
        return priorities.recommend(request);
    }

    @PostMapping("/goals/breakdown")
    public PlanningDtos.GoalBreakdownResponse breakDownGoal(
            @Valid @RequestBody PlanningDtos.GoalBreakdownRequest request) {
        return goalBreakdown.breakDown(request);
    }
}
