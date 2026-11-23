package com.lifepilot.habit;

import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class HabitStreaks {
    private HabitStreaks() {}

    public static int current(List<LocalDate> completedDates, LocalDate asOf) {
        Set<LocalDate> completed = new HashSet<>(completedDates);
        LocalDate cursor = completed.contains(asOf) ? asOf : asOf.minusDays(1);
        int streak = 0;
        while (completed.contains(cursor)) {
            streak++;
            cursor = cursor.minusDays(1);
        }
        return streak;
    }

    public static int longest(List<LocalDate> completedDates) {
        int longest = 0;
        int current = 0;
        LocalDate previous = null;
        for (LocalDate day : completedDates.stream().distinct().sorted().toList()) {
            current = previous != null && day.equals(previous.plusDays(1)) ? current + 1 : 1;
            longest = Math.max(longest, current);
            previous = day;
        }
        return longest;
    }
}
