package com.lifepilot.config;

import java.net.URI;
import java.util.Arrays;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.ConfigurableEnvironment;

public class DatabaseConfigurationValidator
        implements ApplicationContextInitializer<ConfigurableApplicationContext> {

    @Override
    public void initialize(ConfigurableApplicationContext context) {
        validate(context.getEnvironment());
    }

    static void validate(ConfigurableEnvironment environment) {
        String url = required(environment, "spring.datasource.url");
        required(environment, "spring.datasource.username");
        required(environment, "spring.datasource.password");
        if (!url.startsWith("jdbc:postgresql://")) {
            throw new IllegalStateException(
                    "spring.datasource.url must be a PostgreSQL JDBC URL with sslmode=require");
        }
        try {
            URI uri = URI.create(url.substring("jdbc:".length()));
            if (uri.getRawUserInfo() != null) {
                throw new IllegalStateException(
                        "Set datasource username and password separately from spring.datasource.url");
            }
            String query = uri.getRawQuery();
            if (query == null || Arrays.stream(query.split("&"))
                    .noneMatch(parameter -> "sslmode=require".equalsIgnoreCase(parameter))) {
                throw new IllegalStateException(
                        "spring.datasource.url must include sslmode=require");
            }
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException(
                    "spring.datasource.url is not a valid PostgreSQL JDBC URL");
        }
    }

    private static String required(ConfigurableEnvironment environment, String name) {
        String value;
        try {
            value = environment.getProperty(name);
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException(
                    missingConfigurationMessage(name));
        }
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(
                    missingConfigurationMessage(name));
        }
        return value;
    }

    private static String missingConfigurationMessage(String property) {
        String environmentVariable = switch (property) {
            case "spring.datasource.url" -> "DATABASE_URL";
            case "spring.datasource.username" -> "DATABASE_USERNAME";
            case "spring.datasource.password" -> "DATABASE_PASSWORD";
            default -> "the corresponding environment variable";
        };
        return "Required environment variable " + environmentVariable
                + " is not set (required by " + property
                + "). Set it in the process environment before starting the application.";
    }
}
