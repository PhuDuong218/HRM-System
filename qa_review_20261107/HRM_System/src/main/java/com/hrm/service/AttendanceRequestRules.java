package com.hrm.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Objects;

/**
 * Immutable business rules for correction request lifecycle (no database side
 * effects).
 */
public final class AttendanceRequestRules {
    private AttendanceRequestRules() {
    }

    public enum Status {
        CHO_DUYET, CAN_BO_SUNG, DA_DUYET, TU_CHOI
    }

    public enum Action {
        YEU_CAU_BO_SUNG, BO_SUNG, DUYET, TU_CHOI, QUA_HAN_BO_SUNG
    }

    /** The shift start date is excluded; deadline is day 3 at end of day. */
    public static LocalDateTime submissionDeadline(LocalDate shiftStartDate) {
        return Objects.requireNonNull(shiftStartDate).plusDays(3).atTime(LocalTime.MAX);
    }

    public static LocalDateTime supplementDeadline(LocalDate requestedOn) {
        return Objects.requireNonNull(requestedOn).plusDays(3).atTime(LocalTime.MAX);
    }

    public static LocalDateTime exceptionDeadline(LocalDate shiftStartDate) {
        return Objects.requireNonNull(shiftStartDate).plusDays(30).atTime(LocalTime.MAX);
    }

    public static boolean within(LocalDateTime now, LocalDateTime deadline) {
        return !Objects.requireNonNull(now).isAfter(Objects.requireNonNull(deadline));
    }

    public static boolean active(Status status) {
        return status == Status.CHO_DUYET || status == Status.CAN_BO_SUNG;
    }

    public static Status transition(Status current, Action action) {
        Objects.requireNonNull(current);
        Objects.requireNonNull(action);
        return switch (current) {
            case CHO_DUYET -> switch (action) {
                case YEU_CAU_BO_SUNG -> Status.CAN_BO_SUNG;
                case DUYET -> Status.DA_DUYET;
                case TU_CHOI -> Status.TU_CHOI;
                default -> throw new IllegalStateException("Invalid pending request transition");
            };
            case CAN_BO_SUNG -> switch (action) {
                case BO_SUNG -> Status.CHO_DUYET;
                case QUA_HAN_BO_SUNG -> Status.TU_CHOI;
                default -> throw new IllegalStateException("Invalid supplementation transition");
            };
            case DA_DUYET, TU_CHOI -> throw new IllegalStateException("Terminal request state");
        };
    }

    public static void validateProposal(LocalDateTime proposedIn, LocalDateTime proposedOut,
            boolean proposeIn, boolean proposeOut, String reason) {
        if (!proposeIn && !proposeOut)
            throw new IllegalArgumentException("At least one time must be proposed");
        if (proposeIn && proposedIn == null)
            throw new IllegalArgumentException("Proposed check-in is missing");
        if (proposeOut && proposedOut == null)
            throw new IllegalArgumentException("Proposed check-out is missing");
        if (proposeIn && proposeOut && !proposedOut.isAfter(proposedIn))
            throw new IllegalArgumentException("Proposed check-out must be after check-in");
        if (reason == null || reason.isBlank())
            throw new IllegalArgumentException("Reason is required");
    }
}
