package com.hrm.service;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Objects;

/**
 * Pure OT policy: no persistence, no permission assumptions, no database
 * access.
 * A policy guard only; approval and payroll MUST NOT rely on this class alone.
 */
public final class OvertimePolicy {
    private OvertimePolicy() {
    }

    public static final long DAILY_NET_MINUTES_LIMIT = 12L * 60;

    /** Day counting is calendar-based; no weekend/holiday exclusions. */
    public static LocalDate retroactiveLastDate(LocalDate otWorkDate) {
        return Objects.requireNonNull(otWorkDate).plusDays(3);
    }

    public static boolean withinSubmissionWindow(LocalDate otWorkDate, LocalDate submittedOn) {
        Objects.requireNonNull(otWorkDate);
        Objects.requireNonNull(submittedOn);
        return !submittedOn.isAfter(retroactiveLastDate(otWorkDate));
    }

    /**
     * Max days (calendar) an OT may be registered ahead of its work date (answer
     * Q10).
     */
    public static final int MAX_ADVANCE_DAYS = 30;

    public static boolean withinAdvanceWindow(LocalDate otWorkDate, LocalDate submittedOn) {
        Objects.requireNonNull(otWorkDate);
        Objects.requireNonNull(submittedOn);
        return !otWorkDate.isAfter(submittedOn.plusDays(MAX_ADVANCE_DAYS));
    }

    public static void validateRequest(LocalDateTime start, LocalDateTime end, String reason) {
        Objects.requireNonNull(start, "requested start");
        Objects.requireNonNull(end, "requested end");
        if (!end.isAfter(start))
            throw new IllegalArgumentException("requested_end must be after requested_start");
        if (reason == null || reason.isBlank())
            throw new IllegalArgumentException("OT reason is required");
    }

    public static boolean overlaps(LocalDateTime aStart, LocalDateTime aEnd,
            LocalDateTime bStart, LocalDateTime bEnd) {
        if (aStart == null || aEnd == null || bStart == null || bEnd == null ||
                !aEnd.isAfter(aStart) || !bEnd.isAfter(bStart)) {
            throw new IllegalArgumentException("Invalid interval");
        }
        // Half-open [start,end): touching endpoints is not an overlap.
        return aStart.isBefore(bEnd) && bStart.isBefore(aEnd);
    }

    public static long netMinutes(LocalDateTime start, LocalDateTime end, long breakMinutes) {
        if (start == null || end == null || !end.isAfter(start) || breakMinutes < 0)
            throw new IllegalArgumentException("Invalid actual OT interval or break");
        long gross = Duration.between(start, end).toMinutes();
        if (breakMinutes > gross)
            throw new IllegalArgumentException("Break exceeds gross OT duration");
        return gross - breakMinutes;
    }

    /** Caller must provide complete, independently computed daily net totals. */
    public static void requireDailyLimit(long regularNetMinutes, long overtimeNetMinutes) {
        if (regularNetMinutes < 0 || overtimeNetMinutes < 0 ||
                regularNetMinutes > DAILY_NET_MINUTES_LIMIT ||
                overtimeNetMinutes > DAILY_NET_MINUTES_LIMIT ||
                regularNetMinutes + overtimeNetMinutes > DAILY_NET_MINUTES_LIMIT) {
            throw new IllegalArgumentException("Daily net work exceeds 12 hours");
        }
    }

    /** Actual net time cannot be assumed to equal approved/requested time. */
    public static boolean actualWithinApprovedInterval(LocalDateTime approvedStart,
            LocalDateTime approvedEnd,
            LocalDateTime actualStart,
            LocalDateTime actualEnd) {
        validateRequest(approvedStart, approvedEnd, "approved");
        validateRequest(actualStart, actualEnd, "actual");
        return !actualStart.isBefore(approvedStart) && !actualEnd.isAfter(approvedEnd);
    }
}
