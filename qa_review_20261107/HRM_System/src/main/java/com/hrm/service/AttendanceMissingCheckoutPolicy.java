package com.hrm.service;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * Pure deadline classification; no writes, notifications or status transitions.
 */
public final class AttendanceMissingCheckoutPolicy {
    private AttendanceMissingCheckoutPolicy() {
    }

    public static boolean overdue(LocalDateTime plannedEnd,
            LocalDateTime actualStart,
            LocalDateTime actualEnd,
            LocalDateTime serverNow) {
        Objects.requireNonNull(plannedEnd, "plannedEnd");
        Objects.requireNonNull(serverNow, "serverNow");
        return actualStart != null && actualEnd == null
                && serverNow.isAfter(plannedEnd.plusHours(2));
    }
}
