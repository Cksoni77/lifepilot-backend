package com.lifepilot.user;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lifepilot.security.CurrentUser;
import java.time.LocalTime;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

@Service
public class UserPreferencesService {
    private final JdbcTemplate jdbcTemplate;
    private final CurrentUser currentUser;
    private final ObjectMapper objectMapper;

    public UserPreferencesService(
            JdbcTemplate jdbcTemplate, CurrentUser currentUser, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.currentUser = currentUser;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public UserPreferencesDtos.PreferencesResponse get() {
        UUID userId = currentUser.id();
        return jdbcTemplate.query(
                "SELECT timezone, theme, notifications_enabled, reminder_enabled, "
                        + "daily_start_time, daily_end_time FROM user_preferences WHERE user_id = ?",
                resultSet -> resultSet.next()
                        ? new UserPreferencesDtos.PreferencesResponse(
                                resultSet.getString("timezone"),
                                resultSet.getString("theme"),
                                resultSet.getBoolean("notifications_enabled"),
                                resultSet.getBoolean("reminder_enabled"),
                                resultSet.getObject("daily_start_time", LocalTime.class),
                                resultSet.getObject("daily_end_time", LocalTime.class))
                        : new UserPreferencesDtos.PreferencesResponse(
                                "UTC", "system", true, true, null, null),
                userId);
    }

    @Transactional
    public UserPreferencesDtos.PreferencesResponse update(
            UserPreferencesDtos.PreferencesRequest request) {
        jdbcTemplate.update(
                "INSERT INTO user_preferences (user_id, timezone, theme, notifications_enabled, "
                        + "reminder_enabled, daily_start_time, daily_end_time) VALUES (?, ?, ?, ?, ?, ?, ?) "
                        + "ON CONFLICT (user_id) DO UPDATE SET timezone = EXCLUDED.timezone, "
                        + "theme = EXCLUDED.theme, notifications_enabled = EXCLUDED.notifications_enabled, "
                        + "reminder_enabled = EXCLUDED.reminder_enabled, daily_start_time = EXCLUDED.daily_start_time, "
                        + "daily_end_time = EXCLUDED.daily_end_time, updated_at = now()",
                currentUser.id(), request.timezone(), request.theme(),
                request.notificationsEnabled(), request.reminderEnabled(),
                request.dailyStartTime(), request.dailyEndTime());
        return get();
    }

    @Transactional(readOnly = true)
    public List<UserProfile> getProfiles() {
        UUID userId = currentUser.id();
        return jdbcTemplate.query(
                "SELECT profiles::text FROM user_preferences WHERE user_id = ?",
                resultSet -> resultSet.next()
                        ? deserializeProfiles(resultSet.getString(1))
                        : List.of(),
                userId);
    }

    @Transactional
    public List<UserProfile> updateProfiles(List<UserProfile> profiles) {
        if (profiles == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Profiles are required");
        }
        if (profiles.stream().anyMatch(Objects::isNull)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Profile values are required");
        }
        if (profiles.size() > UserProfile.values().length) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Too many profiles");
        }
        if (Set.copyOf(profiles).size() != profiles.size()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Profiles must be unique");
        }

        jdbcTemplate.update(
                "INSERT INTO user_preferences (user_id, profiles) VALUES (?, CAST(? AS jsonb)) "
                        + "ON CONFLICT (user_id) DO UPDATE SET profiles = EXCLUDED.profiles, "
                        + "updated_at = now()",
                currentUser.id(), serializeProfiles(profiles));
        return List.copyOf(profiles);
    }

    private String serializeProfiles(List<UserProfile> profiles) {
        try {
            return objectMapper.writeValueAsString(profiles);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Could not serialize user profiles", exception);
        }
    }

    private List<UserProfile> deserializeProfiles(String value) {
        try {
            return objectMapper.readValue(value, new TypeReference<>() {});
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Stored user profiles are invalid", exception);
        }
    }
}
