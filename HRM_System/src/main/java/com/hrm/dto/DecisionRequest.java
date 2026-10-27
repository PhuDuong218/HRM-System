package com.hrm.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import com.hrm.enums.*;
import jakarta.validation.constraints.*;

public record DecisionRequest(
        @NotNull @Positive Long employeeId,
        @NotBlank @Size(max = 50) @Pattern(regexp = "[A-Za-z0-9_-]+") String decisionNumber,
        @NotNull DecisionType decisionType,
        @NotBlank @Size(max = 2000) String content,
        @NotNull @PastOrPresent LocalDate issuedDate,
        @NotNull DecisionPayrollEffect payrollEffect,
        @DecimalMin("0.0001") @Digits(integer = 15, fraction = 4) BigDecimal amount,
        LocalDate applyDate,
        @NotBlank @Size(max = 500) String reason) {
}