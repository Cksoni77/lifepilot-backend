package com.lifepilot.user;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.LocalTime;
import java.util.List;

public final class UserPreferencesDtos {
    private UserPreferencesDtos() {}

    public record PreferencesRequest(
            @NotBlank @Size(max = 80) String timezone,
            @NotBlank @Pattern(regexp = "light|dark|system") String theme,
            boolean notificationsEnabled,
            boolean reminderEnabled,
            LocalTime dailyStartTime,
            LocalTime dailyEndTime) {}

    public record PreferencesResponse(
            String timezone,
            String theme,
            boolean notificationsEnabled,
            boolean reminderEnabled,
            LocalTime dailyStartTime,
            LocalTime dailyEndTime) {}

    public record ProfilesRequest(
            @jakarta.validation.constraints.NotNull
            @Size(max = 6)
            List<@jakarta.validation.constraints.NotNull UserProfile> profiles) {}

    public record ProfilesResponse(List<UserProfile> profiles) {}
}
