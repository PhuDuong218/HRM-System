package com.hrm.service;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;

/**
 * Deterministic minute-level OT preview. Read-only: does NOT mark OT confirmed,
 * change request statuses, or create payroll entries.
 * Every interval uses [start,end); times must have minute precision.
 * Approved minutes = actual minutes inside approved interval, excluding
 * detailed breaks and regular-work intervals. Daily cap is 720 net minutes.
 * Unapproved/excess minutes remain visible in overflow, never erased.
 */
public final class OvertimeDailyCalculation {
    private OvertimeDailyCalculation() {
    }

    public record Interval(LocalDateTime start, LocalDateTime end) {
        public Interval {
            Objects.requireNonNull(start);
            Objects.requireNonNull(end);
            if (!end.isAfter(start) || start.getSecond() != 0 || start.getNano() != 0
                    || end.getSecond() != 0 || end.getNano() != 0)
                throw new IllegalArgumentException("Intervals must be positive, minute aligned");
        }

        public boolean contains(LocalDateTime t) {
            return !t.isBefore(start) && t.isBefore(end);
        }
    }

    public record DayContext(long confirmedRegularNet, long otherConfirmedOtNet) {
        public DayContext {
            if (confirmedRegularNet < 0 || otherConfirmedOtNet < 0)
                throw new IllegalArgumentException("Negative working time");
        }
    }

    public record DayResult(LocalDate workDate, long actualNet, long withinApproval,
            long validMinutes, long overflowMinutes, long remainingDailyCapacity) {
    }

    public static List<DayResult> calculate(Interval actual, Interval approved,
            List<Interval> detailedBreaks, List<Interval> regularWork,
            Function<LocalDate, DayContext> confirmedDayContext) {
        Objects.requireNonNull(actual);
        Objects.requireNonNull(approved);
        Objects.requireNonNull(confirmedDayContext);
        List<Interval> breaks = detailedBreaks == null ? List.of() : List.copyOf(detailedBreaks);
        List<Interval> regular = regularWork == null ? List.of() : List.copyOf(regularWork);
        if (Duration.between(actual.start(), actual.end()).toDays() > 7)
            throw new IllegalArgumentException("OT longer than seven days requires review");
        for (Interval b : breaks) {
            if (b.start().isBefore(actual.start()) || b.end().isAfter(actual.end()))
                throw new IllegalArgumentException("Break outside actual OT");
        }
        // Break overlap means duplicate subtraction, reject rather than silently
        // normalize.
        for (int i = 0; i < breaks.size(); i++)
            for (int j = i + 1; j < breaks.size(); j++)
                if (overlaps(breaks.get(i), breaks.get(j)))
                    throw new IllegalArgumentException("Overlapping OT breaks");
        Map<LocalDate, long[]> counters = new LinkedHashMap<>();
        for (LocalDateTime t = actual.start(); t.isBefore(actual.end()); t = t.plusMinutes(1)) {
            boolean isBreak = contains(breaks, t);
            boolean isRegular = contains(regular, t);
            long[] counts = counters.computeIfAbsent(t.toLocalDate(), k -> new long[2]);
            // A minute covered by regular work is never also eligible as overtime.
            if (!isBreak && !isRegular) {
                counts[0]++;
                if (approved.contains(t))
                    counts[1]++;
            }
        }
        List<DayResult> result = new ArrayList<>();
        for (var entry : counters.entrySet()) {
            DayContext ctx = Objects.requireNonNull(confirmedDayContext.apply(entry.getKey()),
                    "Confirmed daily attendance data is required");
            long capacity = Math.max(0, 720L - ctx.confirmedRegularNet() - ctx.otherConfirmedOtNet());
            long actualNet = entry.getValue()[0];
            long inApproved = entry.getValue()[1];
            long valid = Math.min(inApproved, capacity);
            result.add(new DayResult(entry.getKey(), actualNet, inApproved, valid,
                    actualNet - valid, capacity));
        }
        return Collections.unmodifiableList(result);
    }

    private static boolean contains(List<Interval> intervals, LocalDateTime t) {
        for (Interval span : intervals)
            if (span.contains(t))
                return true;
        return false;
    }

    private static boolean overlaps(Interval a, Interval b) {
        return a.start().isBefore(b.end()) && b.start().isBefore(a.end());
    }
}
