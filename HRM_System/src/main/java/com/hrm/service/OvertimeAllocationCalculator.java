package com.hrm.service;

import com.hrm.util.TimeIntervalRules;
import com.hrm.util.TimeIntervalRules.Span;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/**
 * Pure (no DB) calculation of confirmed OT per calendar day (OT-20, OT-23,
 * OT-24, OT-25, OT-26). Whole actual time is recorded elsewhere; here only the
 * VALID part is derived and the rest is reported as overflow.
 *
 * valid_d = min(R_d, max(0, 720 - regularNet_d - otherConfirmed_d))
 * R_d = actual_d within approved interval, minus breaks, minus regular schedule
 * A_d = actual_d minus breaks, minus regular schedule (ignores approved
 * interval)
 * overflow_d = A_d - valid_d
 */
public final class OvertimeAllocationCalculator {
    private OvertimeAllocationCalculator() {
    }

    /** Context of one calendar day supplied by the caller (from the DB). */
    public record DayContext(long regularNetMinutes, long otherConfirmedOtMinutes) {
        public DayContext {
            if (regularNetMinutes < 0 || otherConfirmedOtMinutes < 0)
                throw new IllegalArgumentException("Negative day context");
        }
    }

    public record DayAllocation(LocalDate workDate, LocalDateTime segmentStart, LocalDateTime segmentEnd,
            long breakMinutes, long recognizableMinutes, long validMinutes, long overflowMinutes,
            long regularNetMinutes, long otherOtMinutes) {
    }

    /**
     * Splits [start,end) at local midnights; each piece belongs to one calendar
     * day.
     */
    public static List<Span> splitByDay(Span span) {
        List<Span> parts = new ArrayList<>();
        LocalDateTime cursor = span.start();
        while (cursor.isBefore(span.end())) {
            LocalDateTime dayEnd = cursor.toLocalDate().plusDays(1).atStartOfDay();
            LocalDateTime partEnd = span.end().isBefore(dayEnd) ? span.end() : dayEnd;
            parts.add(new Span(cursor, partEnd));
            cursor = partEnd;
        }
        return List.copyOf(parts);
    }

    /**
     * @param detailedBreaks    actual break intervals; may be empty when only a
     *                          total is known
     * @param totalBreakMinutes total break minutes (== sum of detailedBreaks when
     *                          those exist)
     * @param regularSchedule   planned regular intervals (blocked: a minute is
     *                          never regular AND OT)
     */
    public static List<DayAllocation> allocate(Span actual, Span approved, List<Span> detailedBreaks,
            long totalBreakMinutes, List<Span> regularSchedule, Function<LocalDate, DayContext> context) {
        if (totalBreakMinutes < 0)
            throw new IllegalArgumentException("Negative break");
        List<Span> parts = splitByDay(actual);
        boolean detailed = detailedBreaks != null && !detailedBreaks.isEmpty();
        if (!detailed && totalBreakMinutes > 0 && parts.size() > 1)
            throw new IllegalArgumentException("Detailed breaks are required for OT across midnight");
        List<DayAllocation> result = new ArrayList<>();
        for (Span part : parts) {
            List<Span> breaksOfDay = new ArrayList<>();
            long breakMinutes = 0;
            if (detailed) {
                for (Span b : detailedBreaks) {
                    TimeIntervalRules.intersect(part, b).ifPresent(breaksOfDay::add);
                }
                breakMinutes = unionMinutes(breaksOfDay);
            } else if (totalBreakMinutes > 0) {
                breakMinutes = Math.min(totalBreakMinutes, TimeIntervalRules.minutes(part));
            }
            long totalOnly = detailed ? 0 : breakMinutes;
            long recognizable = Math.max(0,
                    TimeIntervalRules.recognizedMinutes(part, part, breaksOfDay, regularSchedule) - totalOnly);
            long withinApproved = Math.max(0,
                    TimeIntervalRules.recognizedMinutes(part, approved, breaksOfDay, regularSchedule) - totalOnly);
            long recognizedInApproved = Math.min(withinApproved, recognizable);
            LocalDate day = part.start().toLocalDate();
            DayContext ctx = context.apply(day);
            long capacity = Math.max(0,
                    OvertimePolicy.DAILY_NET_MINUTES_LIMIT - ctx.regularNetMinutes() - ctx.otherConfirmedOtMinutes());
            long valid = Math.min(recognizedInApproved, capacity);
            result.add(new DayAllocation(day, part.start(), part.end(), breakMinutes, recognizable, valid,
                    recognizable - valid, ctx.regularNetMinutes(), ctx.otherConfirmedOtMinutes()));
        }
        return List.copyOf(result);
    }

    /**
     * Minutes covered by the union of intervals (overlaps are not double counted).
     */
    public static long unionMinutes(List<Span> spans) {
        List<Span> sorted = new ArrayList<>(spans);
        sorted.sort((a, b) -> a.start().compareTo(b.start()));
        long total = 0;
        LocalDateTime cursor = null;
        for (Span s : sorted) {
            LocalDateTime from = cursor == null || s.start().isAfter(cursor) ? s.start() : cursor;
            if (s.end().isAfter(from)) {
                total += TimeIntervalRules.minutes(new Span(from, s.end()));
                cursor = s.end();
            }
        }
        return total;
    }

    /**
     * Net minutes of [start,end) that fall in {@code day}, with the break spread
     * proportionally.
     */
    public static long netMinutesInDay(LocalDateTime start, LocalDateTime end, long breakMinutes, LocalDate day) {
        if (start == null || end == null || !end.isAfter(start) || breakMinutes < 0)
            return 0;
        LocalDateTime dayStart = day.atStartOfDay();
        LocalDateTime dayEnd = day.plusDays(1).atStartOfDay();
        LocalDateTime s = start.isAfter(dayStart) ? start : dayStart;
        LocalDateTime e = end.isBefore(dayEnd) ? end : dayEnd;
        if (!e.isAfter(s))
            return 0;
        long gross = java.time.Duration.between(start, end).toMinutes();
        long inDay = java.time.Duration.between(s, e).toMinutes();
        long brk = gross == 0 ? 0 : Math.round((double) breakMinutes * inDay / gross);
        return Math.max(0, inDay - brk);
    }
}
