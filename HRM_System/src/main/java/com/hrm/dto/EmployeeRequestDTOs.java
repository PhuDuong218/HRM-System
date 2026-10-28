package com.hrm.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public final class EmployeeRequestDTOs {
    private EmployeeRequestDTOs() {
    }

    /**
     * Local Vietnam wall clock, matching work_schedules. Audit timestamps use UTC.
     */
    public record Leave(@NotNull @Positive Long leaveTypeId, @NotNull LocalDateTime leaveStart,
            @NotNull LocalDateTime leaveEnd, @NotBlank @Size(max = 1000) String reason) {
    }

    public record Resignation(@NotNull LocalDate requestedLastWorkingDate, @NotBlank @Size(max = 1000) String reason) {
    }

    public record Decision(LocalDate approvedLastWorkingDate, @Size(max = 1000) String note) {
    }

    public record Rejection(@NotBlank @Size(max = 1000) String reason) {
    }

    public record Policy(@NotNull Boolean consumesEntitlement) {
    }
}
