package com.hrm.service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Objects;

/**
 * Pure business-time rules for employee self check-in/out.
 *
 * This class does NOT provide an endpoint, persist attendance, or authenticate
 * employees. A trusted service must supply the server-observed timestamp,
 * a validated owned schedule, and concurrency-safe database operations.
 */
public final class AttendanceClockPolicy {

    private AttendanceClockPolicy() {
    }

    public static final int CHECK_IN_WINDOW_MINUTES = 15;
    public static final int CHECK_OUT_AFTER_END_MINUTES = 120;

    public record CheckInDecision(boolean allowed, long lateMinutes) {
    }

    public record CheckOutDecision(boolean allowed, boolean earlyDeparture) {
    }

    private static void validateShift(LocalDateTime plannedStart, LocalDateTime plannedEnd) {
        Objects.requireNonNull(plannedStart, "plannedStart");
        Objects.requireNonNull(plannedEnd, "plannedEnd");
        if (!plannedEnd.isAfter(plannedStart)) {
            throw new IllegalArgumentException("The absolute shift end must be after start");
        }
    }

    public static CheckInDecision checkIn(
            LocalDateTime plannedStart, LocalDateTime plannedEnd, LocalDateTime serverNow) {
        validateShift(plannedStart, plannedEnd);
        Objects.requireNonNull(serverNow, "serverNow");
        boolean allowed = !serverNow.isBefore(plannedStart.minusMinutes(CHECK_IN_WINDOW_MINUTES))
                && !serverNow.isAfter(plannedStart.plusMinutes(CHECK_IN_WINDOW_MINUTES));
        // For exact subminute lateness, round UP: any arrival after plannedStart is
        // late.
        long lateMinutes = serverNow.isAfter(plannedStart)
                ? (Duration.between(plannedStart, serverNow).toNanos() + 59_999_999_999L) / 60_000_000_000L
                : 0;
        return new CheckInDecision(allowed, allowed ? lateMinutes : 0);
    }

    public static CheckOutDecision checkOut(
            LocalDateTime plannedStart, LocalDateTime plannedEnd,
            LocalDateTime recordedCheckIn, LocalDateTime serverNow) {
        validateShift(plannedStart, plannedEnd);
        Objects.requireNonNull(serverNow, "serverNow");
        if (recordedCheckIn == null)
            return new CheckOutDecision(false, false);
        boolean allowed = serverNow.isAfter(recordedCheckIn)
                && !serverNow.isAfter(plannedEnd.plusMinutes(CHECK_OUT_AFTER_END_MINUTES));
        return new CheckOutDecision(allowed, allowed && serverNow.isBefore(plannedEnd));
    }

    /** Touching boundaries are allowed, positive-duration overlap is not. */
    public static boolean overlaps(
            LocalDateTime firstStart, LocalDateTime firstEnd,
            LocalDateTime secondStart, LocalDateTime secondEnd) {
        Objects.requireNonNull(firstStart);
        Objects.requireNonNull(firstEnd);
        Objects.requireNonNull(secondStart);
        Objects.requireNonNull(secondEnd);
        if (!firstEnd.isAfter(firstStart) || !secondEnd.isAfter(secondStart)) {
            throw new IllegalArgumentException("Attendance intervals must have positive duration");
        }
        return firstStart.isBefore(secondEnd) && secondStart.isBefore(firstEnd);
    }
}
