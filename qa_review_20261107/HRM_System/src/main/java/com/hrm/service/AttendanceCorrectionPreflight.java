package com.hrm.service;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * Read-only admission and approval preflight rules for attendance correction
 * requests.
 * This intentionally performs NO INSERT/UPDATE and never treats an
 * employee_requests
 * record of another request_type as an attendance request.
 *
 * Service callers MUST load authoritative values from the database, enforce
 * RBAC,
 * protect a complete approval with a transaction/locks, and re-check all rules
 * under those locks; passing preflight alone DOES NOT authorize persistence.
 */
public final class AttendanceCorrectionPreflight {
    private AttendanceCorrectionPreflight() {
    }

    public record ExistingAttendance(LocalDateTime actualIn, LocalDateTime actualOut,
            boolean confirmed, long version) {
    }

    public record Proposal(boolean changeIn, LocalDateTime proposedIn,
            boolean changeOut, LocalDateTime proposedOut,
            String reason) {
    }

    public record AttendanceWindow(LocalDateTime start, LocalDateTime end) {
    }

    public enum Rejection {
        NONE, INVALID_PROPOSAL, NO_SCHEDULE, OUTSIDE_SUBMISSION_DEADLINE,
        ACTIVE_REQUEST_EXISTS, PENDING_UNCONFIRM, ORIGINAL_CHANGED,
        INVALID_INTERVAL, TIME_OVERLAP, PAYROLL_OR_PERIOD_LOCKED
    }

    public record Decision(Rejection reason, LocalDateTime effectiveIn,
            LocalDateTime effectiveOut) {
        public boolean allowed() {
            return reason == Rejection.NONE;
        }
    }

    public static Decision preflight(
            boolean validSchedule, LocalDateTime shiftStart,
            LocalDateTime submittedAt, boolean activeRequestForShift,
            ExistingAttendance current, ExistingAttendance submittedSnapshot,
            Proposal proposal, Iterable<AttendanceWindow> otherValidAttendance,
            boolean periodOrPayrollLocked) {
        Objects.requireNonNull(submittedAt, "submittedAt");
        if (!validSchedule || shiftStart == null)
            return deny(Rejection.NO_SCHEDULE);
        if (!AttendanceRequestRules.within(submittedAt,
                AttendanceRequestRules.submissionDeadline(shiftStart.toLocalDate()))) {
            return deny(Rejection.OUTSIDE_SUBMISSION_DEADLINE);
        }
        if (activeRequestForShift)
            return deny(Rejection.ACTIVE_REQUEST_EXISTS);
        if (!proposalValid(proposal))
            return deny(Rejection.INVALID_PROPOSAL);
        if (periodOrPayrollLocked)
            return deny(Rejection.PAYROLL_OR_PERIOD_LOCKED);
        if (current != null && current.confirmed())
            return deny(Rejection.PENDING_UNCONFIRM);
        if (!sameSnapshot(current, submittedSnapshot))
            return deny(Rejection.ORIGINAL_CHANGED);

        LocalDateTime effectiveIn = proposal.changeIn() ? proposal.proposedIn()
                : current == null ? null : current.actualIn();
        LocalDateTime effectiveOut = proposal.changeOut() ? proposal.proposedOut()
                : current == null ? null : current.actualOut();
        if (effectiveOut != null && (effectiveIn == null || !effectiveOut.isAfter(effectiveIn))) {
            return deny(Rejection.INVALID_INTERVAL);
        }
        if (effectiveIn != null && effectiveOut != null && otherValidAttendance != null) {
            for (AttendanceWindow other : otherValidAttendance) {
                if (other == null || other.start() == null || other.end() == null)
                    continue;
                if (effectiveIn.isBefore(other.end()) && other.start().isBefore(effectiveOut)) {
                    return deny(Rejection.TIME_OVERLAP);
                }
            }
        }
        return new Decision(Rejection.NONE, effectiveIn, effectiveOut);
    }

    private static boolean proposalValid(Proposal p) {
        if (p == null || (!p.changeIn() && !p.changeOut()) ||
                p.reason() == null || p.reason().isBlank())
            return false;
        if (p.changeIn() && p.proposedIn() == null)
            return false;
        if (p.changeOut() && p.proposedOut() == null)
            return false;
        return !p.changeIn() || !p.changeOut() || p.proposedOut().isAfter(p.proposedIn());
    }

    private static boolean sameSnapshot(ExistingAttendance a, ExistingAttendance b) {
        if (a == null || b == null)
            return a == null && b == null;
        return a.version() == b.version() && a.confirmed() == b.confirmed()
                && Objects.equals(a.actualIn(), b.actualIn())
                && Objects.equals(a.actualOut(), b.actualOut());
    }

    private static Decision deny(Rejection r) {
        return new Decision(r, null, null);
    }
}
