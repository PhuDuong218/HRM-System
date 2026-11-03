package com.hrm.dto;
import java.time.LocalDate;
import jakarta.validation.constraints.*;
public record DirectTerminationRequest(@NotBlank @Size(max=1000) String reason,
 @NotNull LocalDate lastWorkingDate,
 @NotNull DecisionType decisionType,
 @NotBlank @Size(max=100) String decisionNumber) {
 public enum DecisionType {MUTUAL_AGREEMENT,CONTRACT_EXPIRY,EMPLOYEE_RESIGNATION,EMPLOYER_DECISION,OTHER}
}
