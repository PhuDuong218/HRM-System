package com.hrm.service;

import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;

/**
 * Read-only classification of attendance anomalies. Does not mutate a record,
 * send notifications, close attendance or perform authorization.
 */
public final class AttendanceAnomalyPolicy {
    private AttendanceAnomalyPolicy() {
    }

    public enum Anomaly {
        MISSING_CHECK_IN, MISSING_CHECK_OUT_EXPIRED, LATE_ARRIVAL, EARLY_DEPARTURE
    }

    public static Set<Anomaly> classify(LocalDateTime plannedStart,
            LocalDateTime plannedEnd,
            LocalDateTime actualStart,
            LocalDateTime actualEnd,
            LocalDateTime serverNow) {
        Objects.requireNonNull(plannedStart, "plannedStart");
        Objects.requireNonNull(plannedEnd, "plannedEnd");
        Objects.requireNonNull(serverNow, "serverNow");
        if (!plannedEnd.isAfter(plannedStart))
            throw new IllegalArgumentException("Shift end must be after start");
        if (actualStart != null && actualEnd != null && !actualEnd.isAfter(actualStart))
            throw new IllegalArgumentException("Check-out must be strictly after check-in");
        EnumSet<Anomaly> result = EnumSet.noneOf(Anomaly.class);
        if (actualStart == null && actualEnd != null)
            result.add(Anomaly.MISSING_CHECK_IN);
        if (actualStart != null && actualEnd == null && serverNow.isAfter(plannedEnd.plusHours(2)))
            result.add(Anomaly.MISSING_CHECK_OUT_EXPIRED);
        if (actualStart != null && actualStart.isAfter(plannedStart))
            result.add(Anomaly.LATE_ARRIVAL);
        if (actualEnd != null && actualEnd.isBefore(plannedEnd))
            result.add(Anomaly.EARLY_DEPARTURE);
        return Set.copyOf(result);
    }
}
