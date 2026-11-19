package com.lifepilot.ai;

import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/ai")
public class AiController {
    private final AiService aiService;

    public AiController(AiService aiService) { this.aiService = aiService; }

    @PostMapping("/chat")
    public AiDtos.ChatResponse chat(@Valid @RequestBody AiDtos.ChatRequest request) {
        return aiService.chat(request);
    }

    @GetMapping("/conversations")
    public List<AiDtos.ConversationResponse> conversations() {
        return aiService.conversations();
    }

    @GetMapping("/conversations/{id}/messages")
    public List<AiDtos.MessageResponse> messages(@PathVariable UUID id) {
        return aiService.messages(id);
    }

    @PostMapping("/daily-plan")
    public AiDtos.DailyPlanResponse dailyPlan() { return aiService.dailyPlan(); }
}
