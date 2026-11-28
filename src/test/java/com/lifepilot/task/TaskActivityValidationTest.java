package com.lifepilot.task;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.lifepilot.goal.GoalRepository;
import com.lifepilot.security.CurrentUser;
import com.lifepilot.user.AppUser;
import com.lifepilot.user.AppUserRepository;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.server.ResponseStatusException;

class TaskActivityValidationTest {
    @Mock private TaskRepository tasks;
    @Mock private AppUserRepository users;
    @Mock private GoalRepository goals;

    private AutoCloseable mocks;
    private UUID userId;
    private TaskService service;

    @BeforeEach
    void setUp() {
        mocks = MockitoAnnotations.openMocks(this);
        userId = UUID.randomUUID();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(userId, null, List.of()));
        service = new TaskService(tasks, users, goals, new CurrentUser());
    }

    @AfterEach
    void tearDown() throws Exception {
        SecurityContextHolder.clearContext();
        mocks.close();
    }

    @Test
    void rejectsUnsupportedProfilesAndTypes() {
        ResponseStatusException invalidProfile = assertThrows(
                ResponseStatusException.class,
                () -> service.create(task(TaskCategory.WORK, "unknown", "task", Map.of())));
        ResponseStatusException invalidType = assertThrows(
                ResponseStatusException.class,
                () -> service.create(task(TaskCategory.WORK, "STUDENT", "meeting", Map.of())));

        assertEquals(HttpStatus.BAD_REQUEST, invalidProfile.getStatusCode());
        assertEquals(HttpStatus.BAD_REQUEST, invalidType.getStatusCode());
    }

    @Test
    void meetingRequiresValidOrderedStartAndEndTimes() {
        TaskDtos.TaskRequest request = task(
                TaskCategory.WORK,
                "WORKING_PROFESSIONAL",
                "meeting",
                Map.of("startAt", "2026-11-27T13:00:00Z", "endAt", "2026-11-27T12:00:00Z"));

        ResponseStatusException exception =
                assertThrows(ResponseStatusException.class, () -> service.create(request));

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
    }

    @Test
    void assignmentNeedsSubjectAndSubmissionDeadline() {
        TaskDtos.TaskRequest request = task(
                TaskCategory.WORK,
                "STUDENT",
                "assignment",
                Map.of("subject", "Physics", "submissionDate", "2026-11-29"));

        ResponseStatusException exception =
                assertThrows(ResponseStatusException.class, () -> service.create(request));

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
    }

    @Test
    void interviewNeedsDateAndCompanyOrLinkedApplication() {
        TaskDtos.TaskRequest request = task(
                TaskCategory.WORK,
                "JOB_SEEKER",
                "interview",
                Map.of("interviewDate", "2026-11-28T15:00:00Z"));

        ResponseStatusException exception =
                assertThrows(ResponseStatusException.class, () -> service.create(request));

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
    }

    @Test
    void billsRequirePositiveAmountAndDueDate() {
        TaskDtos.TaskRequest request = task(
                TaskCategory.PERSONAL,
                "BUSY_INDIVIDUAL",
                "bill",
                Map.of("provider", "Electric utility", "amount", "0",
                        "billDueDate", "2026-11-30"));

        ResponseStatusException exception =
                assertThrows(ResponseStatusException.class, () -> service.create(request));

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
    }

    @Test
    void weeklyHabitRequiresFrequencyTrackingAndWeekdays() {
        TaskDtos.TaskRequest request = task(
                TaskCategory.HEALTH,
                "HABIT_BUILDER",
                "weekly_habit",
                Map.of("habitName", "Run", "frequency", "weekly", "trackingType", "duration"));

        ResponseStatusException exception =
                assertThrows(ResponseStatusException.class, () -> service.create(request));

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
    }

    @Test
    void operationsRequireBusinessActivityAndDeadline() {
        TaskDtos.TaskRequest request = task(
                TaskCategory.WORK,
                "ENTREPRENEUR",
                "operations",
                Map.of("businessMilestone", "Prepare launch"));

        ResponseStatusException exception =
                assertThrows(ResponseStatusException.class, () -> service.create(request));

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
    }

    @Test
    void habitTargetsMustBePositiveNumbers() {
        TaskDtos.TaskRequest request = task(
                TaskCategory.HEALTH,
                "HABIT_BUILDER",
                "daily_habit",
                Map.of("habitName", "Run", "frequency", "daily",
                        "trackingType", "duration", "targetDuration", "-2"));

        ResponseStatusException exception =
                assertThrows(ResponseStatusException.class, () -> service.create(request));

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
    }

    @Test
    void validActivityDetailsAreSavedAndReturnedWithTask() {
        AppUser owner = org.mockito.Mockito.mock(AppUser.class);
        when(users.findById(userId)).thenReturn(Optional.of(owner));
        when(tasks.save(any(TaskEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
        TaskDtos.TaskRequest request = task(
                TaskCategory.WORK,
                "WORKING_PROFESSIONAL",
                "meeting",
                Map.of("startAt", "2026-11-27T13:00:00Z", "endAt", "2026-11-27T14:00:00Z",
                        "attendees", "Team"));

        TaskDtos.TaskResponse response = service.create(request);

        assertEquals("WORKING_PROFESSIONAL", response.activityDetails().profile());
        assertEquals("meeting", response.activityDetails().activityType());
        assertEquals("Team", response.activityDetails().fields().get("attendees"));
        verify(tasks).save(any(TaskEntity.class));
    }

    private TaskDtos.TaskRequest task(
            TaskCategory category, String profile, String type, Map<String, Object> fields) {
        return new TaskDtos.TaskRequest(
                null,
                "Sample activity",
                "",
                category,
                TaskPriority.MEDIUM,
                null,
                null,
                false,
                false,
                null,
                null,
                null,
                new TaskDtos.ActivityDetailsRequest(profile, type, fields));
    }
}
