package com.hrm.service;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Pure calculation for the approved 12-hour NET work limit per calendar day.
 * Callers must supply exact break intervals, never an estimated allocation.
 * This class does not read/write the database and does not change business
 * records.
 */
public final class DailyWorkLimitPolicy {
    public static final long MAX_NET_MINUTES_PER_DAY = 12L * 60;

    private DailyWorkLimitPolicy() {
    }

    public record Interval(LocalDateTime start, LocalDateTime end) {
        public Interval {
            Objects.requireNonNull(start, "start");
            Objects.requireNonNull(end, "end");
            if (!start.isBefore(end)) {
                throw new IllegalArgumentException("Interval must have start < end");
            }
        }
    }

    public record WorkSegment(Interval workedSpan, List<Interval> breaks) {
        public WorkSegment {
            Objects.requireNonNull(workedSpan, "workedSpan");
            breaks = List.copyOf(Objects.requireNonNull(breaks, "breaks"));
            LocalDateTime previousEnd = null;
            for (Interval pause : breaks) {
                if (pause.start().isBefore(workedSpan.start()) || pause.end().isAfter(workedSpan.end())) {
                    throw new IllegalArgumentException("Break is outside work interval");
                }
                if (previousEnd != null && pause.start().isBefore(previousEnd)) {
                    throw new IllegalArgumentException("Breaks must be sorted and non-overlapping");
                }
                previousEnd = pause.end();
            }
        }
    }

    public static Map<LocalDate, Long> netMinutesByDay(List<WorkSegment> segments) {
        Objects.requireNonNull(segments, "segments");
        Map<LocalDate, Long> totals = new HashMap<>();
        for (WorkSegment segment : List.copyOf(segments)) {
            Objects.requireNonNull(segment, "segment");
            addMinutes(totals, segment.workedSpan(), 1);
            for (Interval pause : segment.breaks()) {
                addMinutes(totals, pause, -1);
            }
        }
        return Map.copyOf(totals);
    }

    public static void requireWithinDailyLimit(List<WorkSegment> segments) {
        Map<LocalDate, Long> totals = netMinutesByDay(segments);
        for (Map.Entry<LocalDate, Long> entry : totals.entrySet()) {
            if (entry.getValue() > MAX_NET_MINUTES_PER_DAY) {
                throw new IllegalArgumentException(
                        "Daily net work exceeds 12 hours on " + entry.getKey());
            }
            if (entry.getValue() < 0) {
                throw new IllegalArgumentException("Negative net time on " + entry.getKey());
            }
        }
    }

    /** Split at midnight so overnight work/breaks count on their actual dates. */
    private static void addMinutes(Map<LocalDate, Long> totals, Interval interval, int sign) {
        LocalDateTime cursor = interval.start();
        while (cursor.isBefore(interval.end())) {
            LocalDateTime nextMidnight = cursor.toLocalDate().plusDays(1).atStartOfDay();
            LocalDateTime edge = interval.end().isBefore(nextMidnight) ? interval.end() : nextMidnight;
            long minutes = Duration.between(cursor, edge).toMinutes();
            if (Duration.between(cursor, edge).toSeconds() % 60 != 0) {
                throw new IllegalArgumentException("Intervals must use whole minutes");
            }
            totals.merge(cursor.toLocalDate(), sign * minutes, Long::sum);
            cursor = edge;
        }
    }
}
