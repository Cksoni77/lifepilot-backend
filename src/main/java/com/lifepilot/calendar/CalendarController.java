package com.lifepilot.calendar;

import jakarta.validation.Valid;
import java.time.OffsetDateTime;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/calendar")
public class CalendarController {
    private final CalendarService calendarService;
    private final CalendarTaskService calendarTaskService;

    public CalendarController(
            CalendarService calendarService, CalendarTaskService calendarTaskService) {
        this.calendarService = calendarService;
        this.calendarTaskService = calendarTaskService;
    }

    @GetMapping({"", "/events"})
    public List<CalendarDtos.EventResponse> list() {
        return calendarService.list();
    }

    @GetMapping("/tasks")
    public List<com.lifepilot.task.TaskDtos.TaskResponse> tasksForDate(
            @RequestParam LocalDate date,
            @RequestParam(required = false, defaultValue = "UTC") String timeZone) {
        return calendarTaskService.forDate(date, timeZone);
    }

    @GetMapping("/tasks/range")
    public Map<LocalDate, List<com.lifepilot.task.TaskDtos.TaskResponse>> tasksForRange(
            @RequestParam LocalDate from,
            @RequestParam LocalDate to,
            @RequestParam(required = false, defaultValue = "UTC") String timeZone) {
        return calendarTaskService.forRange(from, to, timeZone);
    }

    @GetMapping("/conflicts")
    public CalendarDtos.ConflictResponse conflicts(
            @RequestParam OffsetDateTime startsAt,
            @RequestParam OffsetDateTime endsAt,
            @RequestParam(required = false) UUID excludeEventId) {
        return calendarService.checkConflicts(startsAt, endsAt, excludeEventId);
    }

    @PostMapping("/events")
    public CalendarDtos.EventResponse create(
            @Valid @RequestBody CalendarDtos.EventRequest request) {
        return calendarService.create(request);
    }

    @PutMapping("/events/{id}")
    public CalendarDtos.EventResponse update(
            @PathVariable UUID id, @Valid @RequestBody CalendarDtos.EventRequest request) {
        return calendarService.update(id, request);
    }

    @DeleteMapping("/events/{id}")
    public void delete(@PathVariable UUID id) {
        calendarService.delete(id);
    }
}
