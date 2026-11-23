package com.lifepilot.calendar;

import com.lifepilot.security.CurrentUser;
import com.lifepilot.task.TaskDtos;
import com.lifepilot.task.TaskRepository;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class CalendarTaskService {
    private final TaskRepository tasks;
    private final CurrentUser currentUser;

    public CalendarTaskService(TaskRepository tasks, CurrentUser currentUser) {
        this.tasks = tasks;
        this.currentUser = currentUser;
    }

    @Transactional(readOnly = true)
    public List<TaskDtos.TaskResponse> forDate(LocalDate date, String timeZone) {
        ZoneId zone = zone(timeZone);
        return forRange(date, date, zone);
    }

    @Transactional(readOnly = true)
    public Map<LocalDate, List<TaskDtos.TaskResponse>> forRange(
            LocalDate from, LocalDate to, String timeZone) {
        if (from == null || to == null || to.isBefore(from)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "from and to are required and to must not precede from");
        }
        if (to.isAfter(from.plusDays(365))) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "Calendar range cannot exceed 366 days");
        }
        ZoneId zone = zone(timeZone);
        return forRange(from, to, zone).stream()
                .collect(Collectors.groupingBy(
                        task -> task.dueDate().atZoneSameInstant(zone).toLocalDate(),
                        java.util.TreeMap::new,
                        Collectors.toList()));
    }

    private List<TaskDtos.TaskResponse> forRange(LocalDate from, LocalDate to, ZoneId zone) {
        OffsetDateTime start = from.atStartOfDay(zone).toOffsetDateTime();
        OffsetDateTime end = to.plusDays(1).atStartOfDay(zone).toOffsetDateTime();
        UUID userId = currentUser.id();
        return tasks.findAllByOwner_IdAndDueAtGreaterThanEqualAndDueAtLessThanOrderByDueAtAsc(
                        userId, start.withOffsetSameInstant(ZoneOffset.UTC),
                        end.withOffsetSameInstant(ZoneOffset.UTC)).stream()
                .map(TaskDtos.TaskResponse::from)
                .toList();
    }

    private ZoneId zone(String timeZone) {
        try {
            return ZoneId.of(timeZone == null || timeZone.isBlank() ? "UTC" : timeZone);
        } catch (DateTimeException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "timeZone must be a valid IANA zone");
        }
    }
}
