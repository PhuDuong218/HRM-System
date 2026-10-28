package com.hrm.dto;

import java.math.BigDecimal;
import java.util.List;

public record AllowanceCalculationResult(Long employeeId, AllowancePeriod period,
        List<AllowanceLine> items, BigDecimal totalRaw, String roundingStatus) {
}