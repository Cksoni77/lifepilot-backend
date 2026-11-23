package com.lifepilot.config;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

class DatabaseConfigurationValidatorTest {
    @Test
    void acceptsNeonJdbcUrlWithSslAndSeparateCredentials() {
        MockEnvironment environment = configuredEnvironment(
                "jdbc:postgresql://neon.example/db?sslmode=require&channel_binding=require");

        assertDoesNotThrow(() -> DatabaseConfigurationValidator.validate(environment));
    }

    @Test
    void rejectsMissingSslAndEmbeddedCredentials() {
        MockEnvironment environment = configuredEnvironment(
                "jdbc:postgresql://neon.example/db");

        assertThrows(IllegalStateException.class,
                () -> DatabaseConfigurationValidator.validate(environment));
        environment.setProperty("spring.datasource.url",
                "jdbc:postgresql://user:pass@neon.example/db?sslmode=require");
        assertThrows(IllegalStateException.class,
                () -> DatabaseConfigurationValidator.validate(environment));
    }

    @Test
    void reportsMissingDatasourcePropertyWithoutShowingValues() {
        MockEnvironment environment = new MockEnvironment()
                .withProperty("spring.datasource.url", "jdbc:postgresql://neon.example/db?sslmode=require");

        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> DatabaseConfigurationValidator.validate(environment));
        org.junit.jupiter.api.Assertions.assertTrue(
                error.getMessage().contains("spring.datasource.username"));
    }

    private static MockEnvironment configuredEnvironment(String url) {
        return new MockEnvironment()
                .withProperty("spring.datasource.url", url)
                .withProperty("spring.datasource.username", "placeholder-user")
                .withProperty("spring.datasource.password", "placeholder-password");
    }
}
