package com.hrm.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public final class OvertimeDTOs {
        private OvertimeDTOs() {
        }

        /** Used for both registration and editing a pending request. */
        public record Register(@NotNull LocalDateTime requestedStart,
                        @NotNull LocalDateTime requestedEnd,
                        @NotBlank String reason) {
        }

        public record Reject(@NotBlank String reason) {
        }

        public record Cancel(@NotBlank String reason) {
        }

        public record ReopenConfirmation(@NotBlank String reason) {
        }

        public record OverflowReview(@NotBlank String note) {
        }

        public record BreakInput(@NotNull LocalDateTime breakStart, @NotNull LocalDateTime breakEnd) {
        }

        /**
         * Whole actual time is recorded as-is. Detailed breaks are preferred; when
         * given, the total is their sum (actualBreakMinutes, if present, must match).
         * reason is mandatory when an existing actual record is being adjusted.
         */
        public record Actual(@NotNull LocalDateTime actualStart,
                        @NotNull LocalDateTime actualEnd,
                        @Min(0) Integer actualBreakMinutes,
                        @Valid List<BreakInput> breaks,
                        String reason) {
        }

        public record BreakView(long breakId, LocalDateTime breakStart, LocalDateTime breakEnd) {
        }

        public record AllocationView(LocalDate workDate, LocalDateTime segmentStart, LocalDateTime segmentEnd,
                        int breakMinutes, int recognizableMinutes, int validMinutes, int overflowMinutes,
                        int regularNetMinutes, int otherOtMinutes) {
        }

        /** status: CHO_DUYET | DA_DUYET | TU_CHOI | DA_HUY (effective status). */
        public record OvertimeView(long overtimeId, long employeeId, String requestCode,
                        LocalDateTime requestedStart, LocalDateTime requestedEnd,
                        String reason, String status, Long processedBy, String rejectionReason,
                        LocalDateTime actualStart, LocalDateTime actualEnd,
                        int actualBreakMinutes, Long confirmedBy,
                        Integer validMinutes, Integer overflowMinutes, String overflowStatus) {
        }

        public record OvertimeDetail(OvertimeView request, List<BreakView> breaks, List<AllocationView> allocations) {
        }

        /**
         * One eligible OT allocation on one calendar day (input for payroll, no money
         * computed).
         */
        public record PayrollInputRow(long employeeId, String employeeCode, long overtimeId, String requestCode,
                        LocalDate workDate, int validMinutes) {
        }

        public record PayrollInputTotal(long employeeId, String employeeCode, long totalValidMinutes) {
        }

        public record PayrollInputs(long periodId, String periodCode, LocalDate periodStart,
                        LocalDate periodEndExclusive,
                        List<PayrollInputTotal> totals, List<PayrollInputRow> rows) {
        }
}
