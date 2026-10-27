package com.hrm.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import com.hrm.enums.PayBasis;
import jakarta.validation.constraints.*;

public record SalaryTermRequest(
    @NotNull LocalDate effectiveFrom,
    LocalDate effectiveTo,
    @NotNull @DecimalMin("0.0000") @Digits(integer=15, fraction=4)
    BigDecimal baseSalary,
    @NotNull PayBasis payBasis,
    @Size(max=500) String documentReference,
    @NotBlank @Size(max=500) String reason
) {}