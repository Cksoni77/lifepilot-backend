package com.lifepilot;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Info;
import com.lifepilot.config.DatabaseConfigurationValidator;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
@OpenAPIDefinition(info = @Info(
        title = "LifePilot Backend API",
        version = "1.0",
        description = "Authenticated personal task, habit, goal, calendar, and planning API"))
public class LifePilotApplication {
    public static void main(String[] args) {
        SpringApplication application = new SpringApplication(LifePilotApplication.class);
        application.addInitializers(new DatabaseConfigurationValidator());
        application.run(args);
    }
}
