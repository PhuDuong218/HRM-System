package com.hrm.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import com.hrm.enums.*;
import jakarta.validation.constraints.*;

public record DecisionRequest(
        @NotNull @Positive Long employeeId,
        @Size(max = 50) @Pattern(regexp = "[A-Za-z0-9_-]*") String decisionNumber,
        @NotNull DecisionType decisionType,
        @NotBlank @Size(max = 2000) String content,
        @NotNull @PastOrPresent LocalDate issuedDate,
        @NotNull DecisionPayrollEffect payrollEffect,
        @DecimalMin("0.0001") @Digits(integer = 15, fraction = 4) BigDecimal amount,
        LocalDate applyDate,
        @NotBlank @Size(max = 500) String reason,
        DisciplineForm disciplineForm,
        LocalDate violationDate,
        @Min(1) @Max(6) Integer salaryIncreaseDelayMonths,
        @Size(max = 30) String rewardCode) {
    public DecisionRequest(Long employeeId, String decisionNumber, DecisionType decisionType, String content,
            LocalDate issuedDate, DecisionPayrollEffect payrollEffect, BigDecimal amount, LocalDate applyDate,
            String reason, DisciplineForm disciplineForm, LocalDate violationDate, Integer salaryIncreaseDelayMonths) {
        this(employeeId, decisionNumber, decisionType, content, issuedDate, payrollEffect, amount, applyDate,
                reason, disciplineForm, violationDate, salaryIncreaseDelayMonths, null);
    }
}
