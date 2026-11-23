package com.lifepilot.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "app")
public record AppProperties(
        @Valid @NotNull Jwt jwt,
        @Valid @NotNull Cors cors,
        @Valid @NotNull OpenAi openai,
        @Valid @NotNull Recommendations recommendations) {

    public record Jwt(
            @NotBlank String secret,
            @Positive long accessTokenMinutes,
            @Positive long refreshTokenDays) {}

    public record Cors(@NotEmpty List<@NotBlank String> allowedOrigins) {
        @AssertTrue(message = "CORS origins must not contain a wildcard")
        public boolean isWildcardDisabled() {
            return allowedOrigins == null || allowedOrigins.stream().noneMatch("*"::equals);
        }
    }

    public record OpenAi(
            String apiKey,
            @NotBlank String model,
            @Positive long timeoutSeconds) {}

    public record Recommendations(
            @NotNull @DecimalMin("0.0") @DecimalMax("1.0") BigDecimal priorityWeight,
            @NotNull @DecimalMin("0.0") @DecimalMax("1.0") BigDecimal urgencyWeight,
            @NotNull @DecimalMin("0.0") @DecimalMax("1.0") BigDecimal deadlineWeight,
            @NotNull @DecimalMin("0.0") @DecimalMax("1.0") BigDecimal goalRelevanceWeight) {

        @AssertTrue(message = "Recommendation weights must sum to 1")
        public boolean isWeightTotalValid() {
            if (priorityWeight == null || urgencyWeight == null
                    || deadlineWeight == null || goalRelevanceWeight == null) {
                return true;
            }
            BigDecimal total = priorityWeight.add(urgencyWeight)
                    .add(deadlineWeight).add(goalRelevanceWeight);
            return total.subtract(BigDecimal.ONE).abs()
                    .compareTo(new BigDecimal("0.0001")) <= 0;
        }
    }
}
