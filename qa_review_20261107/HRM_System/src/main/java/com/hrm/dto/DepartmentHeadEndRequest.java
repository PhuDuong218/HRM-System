package com.hrm.dto;

import java.time.LocalDate;
import jakarta.validation.constraints.*;

/** effectiveFrom is the first date on which the employee no longer acts as head. */
public record DepartmentHeadEndRequest(
        @NotNull LocalDate effectiveFrom,
        @NotNull @Positive Long nextPositionId,
        @NotBlank @Size(max=1000) String reason) {}
