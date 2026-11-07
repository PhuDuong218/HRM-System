package com.hrm.dto;

import java.time.LocalDate;
import jakarta.validation.constraints.*;

public record DepartmentHeadRequest(
        @NotNull @Positive Long employeeId,
        @NotNull LocalDate effectiveFrom,
        @Positive Long previousHeadPositionId,
        @NotBlank @Size(max=1000) String reason) {}
