package com.hrm.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Attendance counts are confirmed records (possibly several shifts per employee/day).
 * Leave hours are elapsed approved leave time, not paid or scheduled working hours.
 * Overtime hours use confirmed actual time, with breaks allocated proportionally to the period.
 */
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
