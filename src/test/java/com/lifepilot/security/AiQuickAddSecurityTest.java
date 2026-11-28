package com.lifepilot.security;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.lifepilot.ai.AiController;
import com.lifepilot.ai.AiDtos;
import com.lifepilot.ai.AiService;
import com.lifepilot.config.AppProperties;
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

@WebMvcTest(AiController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class})
class AiQuickAddSecurityTest {
    @Autowired private MockMvc mvc;
    @MockBean private AiService aiService;
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
    void quickAddRequiresAuthentication() throws Exception {
        mvc.perform(post("/api/ai/quick-add")
                        .contentType("application/json")
                        .content("{\"text\":\"Review interview notes\",\"requestId\":\""
                                + UUID.randomUUID() + "\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void quickAddRejectsInvalidBearerToken() throws Exception {
        when(jwtService.readUserId("bad-token"))
                .thenThrow(new MalformedJwtException("Invalid token"));

        mvc.perform(post("/api/ai/quick-add")
                        .header("Authorization", "Bearer bad-token")
                        .contentType("application/json")
                        .content("{\"text\":\"Review interview notes\",\"requestId\":\""
                                + UUID.randomUUID() + "\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void quickAddAllowsValidBearerToken() throws Exception {
        UUID userId = UUID.randomUUID();
        when(jwtService.readUserId("valid-token")).thenReturn(userId);
        when(aiService.quickAdd(any(AiDtos.QuickAddRequest.class)))
                .thenReturn(new AiDtos.QuickAddResponse(
                        UUID.randomUUID(), List.of(), false, null));

        mvc.perform(post("/api/ai/quick-add")
                        .header("Authorization", "Bearer valid-token")
                        .contentType("application/json")
                        .content("{\"text\":\"Review interview notes\",\"requestId\":\""
                                + UUID.randomUUID() + "\"}"))
                .andExpect(status().isOk());
    }
}
