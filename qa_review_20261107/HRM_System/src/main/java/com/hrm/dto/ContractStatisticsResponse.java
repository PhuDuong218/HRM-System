package com.hrm.dto;

import java.time.LocalDate;

public record ContractStatisticsResponse(
        LocalDate fromDate,
        LocalDate toDate,
        LocalDate reportDate,
        Long departmentId,
        int expiringWithinDays,
        long contractCount,
        long effectiveCount,
        long expiringCount) {
}
