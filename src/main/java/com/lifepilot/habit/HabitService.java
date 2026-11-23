package com.lifepilot.habit;

import com.lifepilot.security.CurrentUser;
import com.lifepilot.user.AppUserRepository;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class HabitService {
    private final HabitRepository habits;
    private final HabitCompletionRepository completions;
    private final AppUserRepository users;
    private final CurrentUser currentUser;

    public HabitService(HabitRepository habits, HabitCompletionRepository completions,
            AppUserRepository users, CurrentUser currentUser) {
        this.habits = habits;
        this.completions = completions;
        this.users = users;
        this.currentUser = currentUser;
    }

    @Transactional(readOnly = true)
    public List<HabitDtos.HabitResponse> list() {
        UUID userId = currentUser.id();
        List<HabitEntity> owned = habits.findAllByOwner_IdOrderByCreatedAtDesc(userId);
        var datesByHabit = completions.findAllByHabit_Owner_IdOrderByCompletedOnAsc(userId).stream()
                .collect(java.util.stream.Collectors.groupingBy(
                        completion -> completion.getHabit().getId(),
                        java.util.stream.Collectors.mapping(
                                HabitCompletion::getCompletedOn,
                                java.util.stream.Collectors.toList())));
        return owned.stream().map(habit -> response(
                habit, datesByHabit.getOrDefault(habit.getId(), List.of()))).toList();
    }

    @Transactional(readOnly = true)
    public HabitDtos.HabitResponse get(UUID id) {
        return response(findOwned(id));
    }

    @Transactional(readOnly = true)
    public HabitDtos.HabitProgress progress(UUID id) {
        HabitEntity habit = findOwned(id);
        List<LocalDate> dates = completions.findAllByHabit_IdOrderByCompletedOnAsc(id).stream()
                .map(HabitCompletion::getCompletedOn).toList();
        return new HabitDtos.HabitProgress(
                id,
                dates.size(),
                HabitStreaks.current(dates, LocalDate.now(ZoneOffset.UTC)),
                HabitStreaks.longest(dates));
    }

    @Transactional
    public HabitDtos.HabitResponse create(HabitDtos.HabitRequest request) {
        validate(request);
        if (request.clientId() != null && !request.clientId().isBlank()) {
            var existing = habits.findByOwner_IdAndClientId(
                    currentUser.id(), request.clientId());
            if (existing.isPresent()) return response(existing.get());
        }
        var owner = users.findById(currentUser.id()).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User not found"));
        HabitEntity habit = habits.save(new HabitEntity(owner, request));
        syncCompletions(habit, request.completedDates());
        return response(habit);
    }

    @Transactional
    public HabitDtos.HabitResponse update(UUID id, HabitDtos.HabitRequest request) {
        validate(request);
        HabitEntity habit = findOwned(id);
        habit.update(request);
        syncCompletions(habit, request.completedDates());
        return response(habit);
    }

    @Transactional
    public void delete(UUID id) {
        habits.delete(findOwned(id));
    }

    @Transactional
    public HabitDtos.HabitResponse complete(UUID id, LocalDate date) {
        HabitEntity habit = findOwned(id);
        if (date.isBefore(habit.getStartDate()) || !isScheduled(habit, date)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "This habit is not scheduled on that date");
        }
        if (completions.findByHabit_IdAndCompletedOn(id, date).isEmpty()) {
            completions.save(new HabitCompletion(habit, date));
        }
        return response(habit);
    }

    @Transactional
    public HabitDtos.HabitResponse undo(UUID id, LocalDate date) {
        HabitEntity habit = findOwned(id);
        completions.findByHabit_IdAndCompletedOn(id, date).ifPresent(completions::delete);
        return response(habit);
    }

    @Transactional(readOnly = true)
    public List<LocalDate> history(UUID id) {
        HabitEntity habit = findOwned(id);
        return completions.findAllByHabit_IdOrderByCompletedOnAsc(habit.getId()).stream()
                .map(HabitCompletion::getCompletedOn).toList();
    }

    @Transactional(readOnly = true)
    public HabitDtos.HabitStatistics statistics() {
        List<HabitEntity> owned = habits.findAllByOwner_IdOrderByCreatedAtDesc(currentUser.id());
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        LocalDate monday = today.with(DayOfWeek.MONDAY);
        var completionsByHabit = completions
                .findAllByHabit_Owner_IdOrderByCompletedOnAsc(currentUser.id()).stream()
                .collect(java.util.stream.Collectors.groupingBy(
                        completion -> completion.getHabit().getId(),
                        java.util.stream.Collectors.mapping(
                                HabitCompletion::getCompletedOn, java.util.stream.Collectors.toSet())));
        int scheduled = 0;
        int completed = 0;
        for (HabitEntity habit : owned) {
            for (LocalDate day = monday; !day.isAfter(today); day = day.plusDays(1)) {
                if (day.isBefore(habit.getStartDate()) || !isScheduled(habit, day)) continue;
                scheduled++;
                if (completionsByHabit.getOrDefault(habit.getId(), java.util.Set.of()).contains(day)) {
                    completed++;
                }
            }
        }
        return new HabitDtos.HabitStatistics(owned.size(), completed, scheduled);
    }

    private HabitEntity findOwned(UUID id) {
        return habits.findByIdAndOwner_Id(id, currentUser.id()).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "Habit not found"));
    }

    private HabitDtos.HabitResponse response(HabitEntity habit) {
        List<LocalDate> dates = completions.findAllByHabit_IdOrderByCompletedOnAsc(habit.getId())
                .stream().map(HabitCompletion::getCompletedOn).toList();
        return response(habit, dates);
    }

    private HabitDtos.HabitResponse response(HabitEntity habit, List<LocalDate> dates) {
        return new HabitDtos.HabitResponse(habit.getId(), habit.getName(), habit.getDescription(),
                "customdays".equals(habit.getFrequency()) ? "customDays" : habit.getFrequency(),
                habit.getSelectedWeekdays(), habit.getTargetDuration(),
                habit.getDurationUnit(), habit.getCategory(), habit.getStartDate(), dates);
    }

    private void syncCompletions(HabitEntity habit, List<LocalDate> dates) {
        if (dates == null) return;
        List<LocalDate> current = completions.findAllByHabit_IdOrderByCompletedOnAsc(habit.getId())
                .stream().map(HabitCompletion::getCompletedOn).toList();
        current.stream().filter(date -> !dates.contains(date))
                .forEach(date -> completions.findByHabit_IdAndCompletedOn(habit.getId(), date)
                        .ifPresent(completions::delete));
        dates.stream().filter(date -> !date.isBefore(habit.getStartDate()))
                .distinct().forEach(date -> completions.findByHabit_IdAndCompletedOn(habit.getId(), date)
                        .orElseGet(() -> completions.save(new HabitCompletion(habit, date))));
    }

    private void validate(HabitDtos.HabitRequest request) {
        String frequency = request.frequency().toLowerCase(java.util.Locale.ROOT);
        if (!java.util.Set.of("daily", "weekdays", "weekends", "customdays")
                .contains(frequency)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported habit frequency");
        }
        if ("customdays".equals(frequency)
                && (request.selectedWeekdays() == null || request.selectedWeekdays().isEmpty())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Select at least one weekday for a custom schedule");
        }
        if (request.selectedWeekdays() != null
                && request.selectedWeekdays().stream().anyMatch(day -> day < 1 || day > 7)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Weekdays must be from 1 to 7");
        }
        if (request.durationUnit() != null
                && !java.util.Set.of("minutes", "hours")
                        .contains(request.durationUnit().toLowerCase(java.util.Locale.ROOT))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported habit duration unit");
        }
        if (request.category() != null
                && !java.util.Set.of("health", "fitness", "personal", "learning", "work", "other")
                        .contains(request.category().toLowerCase(java.util.Locale.ROOT))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported habit category");
        }
    }

    private boolean isScheduled(HabitEntity habit, LocalDate day) {
        return switch (habit.getFrequency().toLowerCase(java.util.Locale.ROOT)) {
            case "weekdays" -> day.getDayOfWeek().getValue() <= 5;
            case "weekends" -> day.getDayOfWeek().getValue() >= 6;
            case "customdays" -> habit.getSelectedWeekdays().contains(day.getDayOfWeek().getValue());
            default -> true;
        };
    }
}
