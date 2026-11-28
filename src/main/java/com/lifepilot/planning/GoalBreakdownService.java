package com.lifepilot.planning;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lifepilot.ai.OllamaClient;
import com.lifepilot.common.SafeApiException;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

@Service
public class GoalBreakdownService {
    private final OllamaClient ollama;
    private final ObjectMapper objectMapper;

    public GoalBreakdownService(OllamaClient ollama, ObjectMapper objectMapper) {
        this.ollama = ollama;
        this.objectMapper = objectMapper;
    }

    public PlanningDtos.GoalBreakdownResponse breakDown(
            PlanningDtos.GoalBreakdownRequest request) {
        if (!ollama.isEnabled()) {
            throw new SafeApiException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Local AI is disabled on this server.");
        }
        if (request.targetDate() != null
                && request.targetDate().isBefore(LocalDate.now(ZoneOffset.UTC))) {
            throw new SafeApiException(HttpStatus.BAD_REQUEST,
                    "Target date must not be in the past.");
        }
        String response;
        try {
            response = ollama.breakDownGoal(request.title().trim(),
                    request.description() == null ? "" : request.description().trim(),
                    request.category().name(), request.targetDate());
        } catch (ResourceAccessException exception) {
            throw new SafeApiException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Could not reach local Ollama. Start Ollama and try again.");
        } catch (RestClientResponseException exception) {
            throw new SafeApiException(HttpStatus.BAD_GATEWAY,
                    "Ollama could not process the goal breakdown.");
        } catch (RestClientException | IllegalStateException exception) {
            throw new SafeApiException(HttpStatus.BAD_GATEWAY,
                    "Ollama returned an unavailable response.");
        }

        PlanningDtos.GoalBreakdownResponse parsed;
        try {
            parsed = objectMapper.readValue(
                    response, PlanningDtos.GoalBreakdownResponse.class);
        } catch (JsonProcessingException exception) {
            throw new SafeApiException(HttpStatus.BAD_GATEWAY,
                    "Ollama returned an invalid goal breakdown.");
        }
        validate(parsed, request.targetDate());
        return parsed;
    }

    private void validate(
            PlanningDtos.GoalBreakdownResponse response, LocalDate targetDate) {
        if (response == null || response.activities() == null
                || response.activities().isEmpty() || response.activities().size() > 12) {
            throw invalidResponse();
        }
        Set<String> titles = new HashSet<>();
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        for (int index = 0; index < response.activities().size(); index++) {
            PlanningDtos.GoalActivitySuggestion activity = response.activities().get(index);
            if (activity == null || activity.title() == null || activity.title().isBlank()
                    || activity.title().length() > 200
                    || activity.description() == null || activity.description().length() > 1000
                    || activity.category() == null || activity.priority() == null
                    || activity.estimatedMinutes() == null || activity.estimatedMinutes() < 1
                    || activity.estimatedMinutes() > 1440
                    || activity.completionCriteria() == null
                    || activity.completionCriteria().isBlank()
                    || activity.completionCriteria().length() > 500) {
                throw invalidResponse();
            }
            if (!titles.add(activity.title().trim().toLowerCase(java.util.Locale.ROOT))) {
                throw invalidResponse();
            }
            if (activity.suggestedDueDate() != null
                    && (targetDate == null || activity.suggestedDueDate().isBefore(today)
                            || activity.suggestedDueDate().isAfter(targetDate))) {
                throw invalidResponse();
            }
            List<Integer> dependencies = activity.dependsOnIndices();
            int currentIndex = index;
            if (dependencies == null || dependencies.size() > 12
                    || dependencies.stream().anyMatch(dependency ->
                            dependency == null || dependency < 0 || dependency >= currentIndex)) {
                throw invalidResponse();
            }
            if (activity.milestone() != null && activity.milestone().length() > 200) {
                throw invalidResponse();
            }
        }
    }

    private SafeApiException invalidResponse() {
        return new SafeApiException(HttpStatus.BAD_GATEWAY,
                "Ollama returned an invalid goal breakdown.");
    }
}
