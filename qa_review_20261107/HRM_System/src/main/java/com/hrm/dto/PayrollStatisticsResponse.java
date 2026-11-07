package com.hrm.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record PayrollStatisticsResponse(
        LocalDate fromDate,
        LocalDate toDate,
        int batchCount,
        long payrollRecordCount,
        Amounts totals,
        List<BatchSummary> batches) {

    /** Allowances and bonuses are already included in grossPayroll. */
    public record Amounts(
            BigDecimal grossPayroll,
            BigDecimal allowances,
            BigDecimal bonuses,
            BigDecimal deductions,
            BigDecimal netPayroll) {
    }

    public record BatchSummary(
            Long periodId,
            String periodCode,
            LocalDate periodStart,
            LocalDate periodEndExclusive,
            Long batchId,
            Integer versionNo,
            long employeeCount,
            Amounts totals) {
    }
}
