package com.hrm.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record DecisionPayrollResult(Long employeeId, Long periodId, LocalDate from, LocalDate to,
        List<DecisionPayrollLine> items, BigDecimal totalAdd, BigDecimal totalDeduct,
        BigDecimal netAdjustment, String policyVersion) {
}