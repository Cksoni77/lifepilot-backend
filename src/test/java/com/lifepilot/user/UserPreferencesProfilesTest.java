package com.lifepilot.user;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lifepilot.security.CurrentUser;
import java.util.List;
import java.util.UUID;
import java.sql.ResultSet;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.server.ResponseStatusException;

class UserPreferencesProfilesTest {
    @Mock private JdbcTemplate jdbcTemplate;

    private AutoCloseable mocks;
    private UUID authenticatedUser;
    private UserPreferencesService service;

    @BeforeEach
    void setUp() {
        mocks = MockitoAnnotations.openMocks(this);
        authenticatedUser = UUID.randomUUID();
        authenticateAs(authenticatedUser);
        service = new UserPreferencesService(jdbcTemplate, new CurrentUser(), new ObjectMapper());
    }

    @AfterEach
    void tearDown() throws Exception {
        SecurityContextHolder.clearContext();
        mocks.close();
    }

    @Test
    void savesMultipleProfilesForTheAuthenticatedUser() {
        List<UserProfile> profiles = List.of(
                UserProfile.WORKING_PROFESSIONAL, UserProfile.HABIT_BUILDER);

        assertEquals(profiles, service.updateProfiles(profiles));

        ArgumentCaptor<String> json = ArgumentCaptor.forClass(String.class);
        verify(jdbcTemplate).update(anyString(), eq(authenticatedUser), json.capture());
        assertEquals("[\"WORKING_PROFESSIONAL\",\"HABIT_BUILDER\"]", json.getValue());
    }

    @Test
    void retrievesMultipleProfilesForTheAuthenticatedUser() throws Exception {
        org.mockito.Mockito.when(jdbcTemplate.query(
                anyString(),
                org.mockito.ArgumentMatchers.<org.springframework.jdbc.core.ResultSetExtractor<List<UserProfile>>>any(),
                eq(authenticatedUser)))
                .thenAnswer(invocation -> {
                    @SuppressWarnings("unchecked")
                    org.springframework.jdbc.core.ResultSetExtractor<List<UserProfile>> resultExtractor =
                            invocation.getArgument(1);
                    ResultSet resultSet = org.mockito.Mockito.mock(ResultSet.class);
                    org.mockito.Mockito.when(resultSet.next()).thenReturn(true);
                    org.mockito.Mockito.when(resultSet.getString(1))
                            .thenReturn("[\"STUDENT\",\"JOB_SEEKER\"]");
                    return resultExtractor.extractData(resultSet);
                });

        assertEquals(
                List.of(UserProfile.STUDENT, UserProfile.JOB_SEEKER),
                service.getProfiles());
        verify(jdbcTemplate).query(
                anyString(),
                org.mockito.ArgumentMatchers.<org.springframework.jdbc.core.ResultSetExtractor<List<UserProfile>>>any(),
                eq(authenticatedUser));
    }

    @Test
    void subsequentUpdateReplacesSelectedProfiles() {
        service.updateProfiles(List.of(UserProfile.STUDENT, UserProfile.JOB_SEEKER));
        List<UserProfile> replacement = List.of(UserProfile.BUSY_INDIVIDUAL);

        assertEquals(replacement, service.updateProfiles(replacement));

        ArgumentCaptor<String> json = ArgumentCaptor.forClass(String.class);
        verify(jdbcTemplate, org.mockito.Mockito.times(2))
                .update(anyString(), eq(authenticatedUser), json.capture());
        assertEquals(
                List.of("[\"STUDENT\",\"JOB_SEEKER\"]", "[\"BUSY_INDIVIDUAL\"]"),
                json.getAllValues());
    }

    @Test
    void rejectsDuplicateProfilesWithoutWriting() {
        assertThrows(ResponseStatusException.class, () -> service.updateProfiles(
                List.of(UserProfile.STUDENT, UserProfile.STUDENT)));
        verifyNoInteractions(jdbcTemplate);
    }

    @Test
    void profileWritesAreScopedToTheCurrentAuthenticatedUser() {
        UUID anotherUser = UUID.randomUUID();
        authenticateAs(anotherUser);

        service.updateProfiles(List.of(UserProfile.ENTREPRENEUR));

        verify(jdbcTemplate).update(anyString(), eq(anotherUser), anyString());
    }

    private void authenticateAs(UUID userId) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(userId, null, List.of()));
    }
}
