package com.lifepilot;

import com.lifepilot.config.AppProperties;
import java.math.BigDecimal;
import java.util.List;

public final class TestAppProperties {
    private TestAppProperties() {}

    public static AppProperties defaults() {
        return create(15, 30, "0.30", "0.30", "0.20", "0.20", "");
    }

    public static AppProperties create(
            long accessMinutes, long refreshDays,
            String priority, String urgency, String deadline, String goal, String apiKey) {
        return create("MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=",
                accessMinutes, refreshDays, priority, urgency, deadline, goal, apiKey);
    }

    public static AppProperties create(
            String secret, long accessMinutes, long refreshDays,
            String priority, String urgency, String deadline, String goal, String apiKey) {
        return new AppProperties(
                new AppProperties.Jwt(secret, accessMinutes, refreshDays),
                new AppProperties.Cors(List.of("http://localhost:3000")),
                new AppProperties.OpenAi(apiKey, "gpt-4.1-mini", 20),
                new AppProperties.Recommendations(
                        new BigDecimal(priority), new BigDecimal(urgency),
                        new BigDecimal(deadline), new BigDecimal(goal)));
    }
}
