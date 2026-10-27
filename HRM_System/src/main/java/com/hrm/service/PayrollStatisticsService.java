package com.hrm.service;

import com.hrm.dto.PayrollStatisticsResponse;
import com.hrm.dto.PayrollStatisticsResponse.Amounts;
import com.hrm.dto.PayrollStatisticsResponse.BatchSummary;
import com.hrm.repository.PayrollStatisticsRepository;
import com.hrm.repository.projection.PayrollStatisticsRow;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Service
public class PayrollStatisticsService {

    public static final String VIEW_AUTHORIZATION = "hasAnyRole('ADMIN','HR')"
            + " and hasAuthority('REPORT_VIEW') and hasAuthority('PAYROLL_VIEW')";

    private static final BigDecimal ZERO = BigDecimal.ZERO.setScale(4);
    private static final LocalDate LAST_REPORT_DATE = LocalDate.of(9999, 12, 30);

    private final PayrollStatisticsRepository repository;

    public PayrollStatisticsService(PayrollStatisticsRepository repository) {
        this.repository = repository;
    }

    @PreAuthorize(VIEW_AUTHORIZATION)
    @Transactional(readOnly = true)
    public PayrollStatisticsResponse summarize(LocalDate fromDate, LocalDate toDate) {
        if (fromDate == null || toDate == null) {
            throw new IllegalArgumentException("fromDate và toDate là bắt buộc");
        }
        if (fromDate.isAfter(toDate)) {
            throw new IllegalArgumentException("fromDate không được sau toDate");
        }
        // The exclusive end must also fit SQL Server's date range.
        if (fromDate.getYear() < 1 || toDate.isAfter(LAST_REPORT_DATE)) {
            throw new IllegalArgumentException("Kỳ báo cáo phải nằm từ 0001-01-01 đến 9999-12-30");
        }

        List<PayrollStatisticsRow> rows = repository.summarizeFinalizedBatches(fromDate, toDate.plusDays(1));
        List<BatchSummary> batches = new ArrayList<>();
        long payrollRecordCount = 0;
        Amounts totals = amounts(ZERO, ZERO, ZERO, ZERO);

        for (PayrollStatisticsRow row : rows) {
            Amounts batchTotals = amounts(
                    row.getGrossPayroll(), row.getAllowances(), row.getBonuses(), row.getDeductions());
            long employeeCount = row.getEmployeeCount();
            batches.add(new BatchSummary(
                    row.getPeriodId(), row.getPeriodCode(), row.getPeriodStart(), row.getPeriodEndExclusive(),
                    row.getBatchId(), row.getVersionNo(), employeeCount, batchTotals));
            payrollRecordCount += employeeCount;
            totals = amounts(
                    totals.grossPayroll().add(batchTotals.grossPayroll()),
                    totals.allowances().add(batchTotals.allowances()),
                    totals.bonuses().add(batchTotals.bonuses()),
                    totals.deductions().add(batchTotals.deductions()));
        }

        return new PayrollStatisticsResponse(
                fromDate, toDate, batches.size(), payrollRecordCount, totals, List.copyOf(batches));
    }

    private Amounts amounts(BigDecimal gross, BigDecimal allowances, BigDecimal bonuses, BigDecimal deductions) {
        return new Amounts(gross, allowances, bonuses, deductions, gross.subtract(deductions));
    }
}
