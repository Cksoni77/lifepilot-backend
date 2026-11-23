package com.lifepilot.task;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/tasks")
public class TaskController {
    private final TaskService taskService;

    public TaskController(TaskService taskService) {
        this.taskService = taskService;
    }

    @GetMapping
    public ResponseEntity<List<TaskDtos.TaskResponse>> list(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String sort,
            @RequestParam(required = false) TaskCategory category,
            @RequestParam(required = false) TaskPriority priority,
            @RequestParam(required = false) Boolean completed,
            @RequestParam(required = false) LocalDate dueFrom,
            @RequestParam(required = false) LocalDate dueTo,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size) {
        List<TaskDtos.TaskResponse> result = taskService.list(
                search, status, sort, category, priority, completed, dueFrom, dueTo);
        if (page == null && size == null) return ResponseEntity.ok(result);
        int pageNumber = page == null ? 0 : page;
        int pageSize = size == null ? 50 : size;
        if (pageNumber < 0 || pageSize < 1 || pageSize > 200) {
            throw new org.springframework.web.server.ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "page must be non-negative and size must be from 1 to 200");
        }
        long start = (long) pageNumber * pageSize;
        int fromIndex = (int) Math.min(start, result.size());
        int toIndex = (int) Math.min(start + pageSize, result.size());
        return ResponseEntity.ok()
                .header("X-Total-Count", Integer.toString(result.size()))
                .header("X-Page", Integer.toString(pageNumber))
                .header("X-Page-Size", Integer.toString(pageSize))
                .body(result.subList(fromIndex, toIndex));
    }

    @GetMapping("/today")
    public List<TaskDtos.TaskResponse> today() {
        return taskService.list(null, "today", null, null, null, null, null, null);
    }

    @GetMapping("/overdue")
    public List<TaskDtos.TaskResponse> overdue() {
        return taskService.list(null, "overdue", null, null, null, null, null, null);
    }

    @GetMapping("/upcoming")
    public List<TaskDtos.TaskResponse> upcoming() {
        return taskService.list(null, "pending", "dueDate", null, null, null, null, null).stream()
                .filter(task -> task.dueDate() != null
                        && !task.dueDate().withOffsetSameInstant(ZoneOffset.UTC)
                                .toLocalDate().isBefore(LocalDate.now(ZoneOffset.UTC)))
                .toList();
    }

    @GetMapping("/reminders/upcoming")
    public List<TaskDtos.TaskResponse> upcomingReminders(
            @RequestParam(required = false) OffsetDateTime from,
            @RequestParam(required = false) OffsetDateTime to) {
        OffsetDateTime start = from == null ? OffsetDateTime.now(ZoneOffset.UTC) : from;
        OffsetDateTime end = to == null ? start.plusDays(7) : to;
        if (!end.isAfter(start)) {
            throw new org.springframework.web.server.ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "to must be after from");
        }
        return taskService.upcomingReminders(start, end);
    }

    @GetMapping("/{id}")
    public TaskDtos.TaskResponse get(@PathVariable UUID id) {
        return taskService.get(id);
    }

    @PostMapping
    public TaskDtos.TaskResponse create(@Valid @RequestBody TaskDtos.TaskRequest request) {
        return taskService.create(request);
    }

    @PutMapping("/{id}")
    public TaskDtos.TaskResponse update(
            @PathVariable UUID id, @Valid @RequestBody TaskDtos.TaskRequest request) {
        return taskService.update(id, request);
    }

    @DeleteMapping("/{id}")
    public void delete(@PathVariable UUID id) {
        taskService.delete(id);
    }

    @PatchMapping("/{id}/complete")
    public TaskDtos.TaskResponse complete(
            @PathVariable UUID id, @Valid @RequestBody CompletionRequest request) {
        return taskService.complete(id, request.completed());
    }

    public record CompletionRequest(@NotNull Boolean completed) {}
}
