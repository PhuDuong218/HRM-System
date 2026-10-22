package com.hrm.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record WorkStatisticsResponse(
        LocalDate fromDate,
        LocalDate toDate,
        long confirmedWorkDays,
        long confirmedAbsentDays,
        long approvedLeaveRequests,
        BigDecimal approvedLeaveHours,
        long approvedOvertimeRequests,
        BigDecimal approvedOvertimeHours) {
}
