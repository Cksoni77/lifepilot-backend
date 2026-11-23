package com.lifepilot.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.zaxxer.hikari.HikariDataSource;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

class AppPropertiesTest {
    private static final String VALID_JWT_SECRET =
            "MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=";
    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(PropertiesConfiguration.class)
            .withConfiguration(AutoConfigurations.of(DataSourceAutoConfiguration.class))
            .withPropertyValues(
                    "app.jwt.secret=" + VALID_JWT_SECRET,
                    "app.jwt.access-token-minutes=15",
                    "app.jwt.refresh-token-days=30",
                    "app.cors.allowed-origins=https://example.test,http://localhost:8081",
                    "app.openai.api-key=",
                    "app.openai.model=gpt-4.1-mini",
                    "app.openai.timeout-seconds=20",
                    "app.recommendations.priority-weight=0.30",
                    "app.recommendations.urgency-weight=0.30",
                    "app.recommendations.deadline-weight=0.20",
                    "app.recommendations.goal-relevance-weight=0.20",
                    "spring.datasource.url=jdbc:postgresql://db.example/lifepilot?sslmode=require",
                    "spring.datasource.username=placeholder-user",
                    "spring.datasource.password=placeholder-password",
                    "spring.datasource.driver-class-name=org.postgresql.Driver",
                    "spring.datasource.hikari.maximum-pool-size=7",
                    "spring.datasource.hikari.minimum-idle=2",
                    "spring.datasource.hikari.connection-timeout=5000");

    @Test
    void bindsAppPropertiesAndHikariSettings() {
        runner.run(context -> {
            assertTrue(context.isRunning());
            AppProperties properties = context.getBean(AppProperties.class);
            assertEquals(15, properties.jwt().accessTokenMinutes());
            assertEquals(30, properties.jwt().refreshTokenDays());
            assertEquals(List.of("https://example.test", "http://localhost:8081"),
                    properties.cors().allowedOrigins());
            assertEquals("", properties.openai().apiKey());
            assertEquals("gpt-4.1-mini", properties.openai().model());
            assertEquals(20, properties.openai().timeoutSeconds());
            assertEquals(new BigDecimal("0.30"),
                    properties.recommendations().priorityWeight());
            assertEquals(new BigDecimal("0.30"),
                    properties.recommendations().urgencyWeight());
            assertEquals(new BigDecimal("0.20"),
                    properties.recommendations().deadlineWeight());
            assertEquals(new BigDecimal("0.20"),
                    properties.recommendations().goalRelevanceWeight());

            HikariDataSource dataSource = context.getBean(HikariDataSource.class);
            assertEquals(7, dataSource.getMaximumPoolSize());
            assertEquals(2, dataSource.getMinimumIdle());
            assertEquals(5000, dataSource.getConnectionTimeout());
        });
    }

    @Test
    void rejectsMissingJwtSecretAndInvalidWeightTotal() {
        runner.withPropertyValues("app.jwt.secret=")
                .run(context -> assertTrue(context.getStartupFailure() != null));
        runner.withPropertyValues("app.recommendations.priority-weight=0.40")
                .run(context -> assertTrue(context.getStartupFailure() != null));
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(AppProperties.class)
    static class PropertiesConfiguration {}
}
