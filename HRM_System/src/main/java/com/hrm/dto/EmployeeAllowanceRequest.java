package com.hrm.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import com.hrm.enums.AllowanceCalculationBasis;
import jakarta.validation.constraints.*;

public record EmployeeAllowanceRequest(
    @NotNull @Positive Long allowanceTypeId,
    @NotNull LocalDate effectiveFrom, LocalDate effectiveTo,
    @NotNull AllowanceCalculationBasis calculationBasis,
    @NotNull @DecimalMin("0.0000") @Digits(integer=15,fraction=4) BigDecimal amount,
    @Size(max=1000) String conditionNote,
    @NotBlank @Size(max=500) String reason
) {}