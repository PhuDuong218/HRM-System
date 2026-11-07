package com.hrm.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import com.hrm.enums.*;

public record DecisionPayrollLine(String sourceKey, Long decisionId, String decisionNumber,
        DecisionType decisionType, LocalDate applyDate, DecisionPayrollEffect direction,
        BigDecimal amount) {
}