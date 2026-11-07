package com.hrm.dto;

import com.hrm.enums.DecisionApprovalStatus;
import com.hrm.enums.DecisionPayrollEffect;
import com.hrm.enums.DecisionType;
import com.hrm.enums.DisciplineForm;
import java.math.BigDecimal;
import java.time.LocalDate;

public record MyDecisionResponse(
        Long decisionId, String decisionNumber, DecisionType decisionType, String content, LocalDate issuedDate,
        DecisionApprovalStatus approvalStatus, DecisionPayrollEffect payrollEffect, BigDecimal amount,
        LocalDate applyDate, DisciplineForm disciplineForm, LocalDate violationDate, Integer salaryIncreaseDelayMonths) {}
