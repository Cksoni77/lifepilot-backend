package com.lifepilot.security;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.lifepilot.config.AppProperties;
import com.lifepilot.planning.GoalBreakdownService;
import com.lifepilot.planning.PlanningController;
import com.lifepilot.planning.PlanningDtos;
import com.lifepilot.planning.PriorityRecommendationService;
import io.jsonwebtoken.MalformedJwtException;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(PlanningController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class})
class PlanningSecurityTest {
    @Autowired private MockMvc mvc;
    @MockBean private PriorityRecommendationService priorities;
    @MockBean private GoalBreakdownService goalBreakdown;
    @MockBean private JwtService jwtService;

    @org.springframework.boot.test.context.TestConfiguration
    static class TestPropertiesConfiguration {
        @Bean
        AppProperties appProperties() {
            return new AppProperties(
                    new AppProperties.Jwt("AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=", 15, 30),
                    new AppProperties.Cors(List.of("http://localhost")),
                    new AppProperties.Ollama(true, "http://localhost:11434", "qwen3.5:4b", 3, 120),
                    new AppProperties.Recommendations(
                            BigDecimal.valueOf(0.30), BigDecimal.valueOf(0.30),
                            BigDecimal.valueOf(0.20), BigDecimal.valueOf(0.20)),
                    new AppProperties.PriorityScoring(
                            BigDecimal.valueOf(0.30), BigDecimal.valueOf(0.30),
                            BigDecimal.valueOf(0.25), BigDecimal.valueOf(0.15)));
        }
    }

    @Test
    void priorityRecommendationRequiresAuthentication() throws Exception {
        mvc.perform(post("/api/planning/priority/recommend")
                        .contentType("application/json")
                        .content("{\"taskId\":\"" + UUID.randomUUID()
                                + "\",\"urgency\":3,\"importance\":3,"
                                + "\"consequenceOfDelay\":3}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void goalBreakdownRequiresAuthentication() throws Exception {
        mvc.perform(post("/api/planning/goals/breakdown")
                        .contentType("application/json")
                        .content("{\"title\":\"Learn Spring\","
                                + "\"category\":\"WORK\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void authenticatedUserCanCallPlanningEndpoints() throws Exception {
        when(jwtService.readUserId("valid-token")).thenReturn(UUID.randomUUID());
        when(priorities.recommend(any(PlanningDtos.PriorityRequest.class)))
                .thenReturn(new PlanningDtos.PriorityResponse(
                        UUID.randomUUID(), "Task", com.lifepilot.task.TaskPriority.LOW,
                        com.lifepilot.task.TaskPriority.HIGH, 75, "Reason", "LOW", "Next"));
        when(goalBreakdown.breakDown(any(PlanningDtos.GoalBreakdownRequest.class)))
                .thenReturn(new PlanningDtos.GoalBreakdownResponse(List.of()));

        mvc.perform(post("/api/planning/priority/recommend")
                        .header("Authorization", "Bearer valid-token")
                        .contentType("application/json")
                        .content("{\"taskId\":\"" + UUID.randomUUID()
                                + "\",\"urgency\":3,\"importance\":3,"
                                + "\"consequenceOfDelay\":3}"))
                .andExpect(status().isOk());
        mvc.perform(post("/api/planning/goals/breakdown")
                        .header("Authorization", "Bearer valid-token")
                        .contentType("application/json")
                        .content("{\"title\":\"Learn Spring\","
                                + "\"category\":\"WORK\"}"))
                .andExpect(status().isOk());
    }

    @Test
    void planningEndpointsRejectInvalidToken() throws Exception {
        when(jwtService.readUserId("invalid-token"))
                .thenThrow(new MalformedJwtException("Invalid token"));

        mvc.perform(post("/api/planning/priority/recommend")
                        .header("Authorization", "Bearer invalid-token")
                        .contentType("application/json")
                        .content("{\"taskId\":\"" + UUID.randomUUID()
                                + "\",\"urgency\":3,\"importance\":3,"
                                + "\"consequenceOfDelay\":3}"))
                .andExpect(status().isUnauthorized());
    }
}
