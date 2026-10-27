package com.hrm.service;

import com.hrm.dto.ContractStatisticsResponse;
import com.hrm.dto.EmployeeStatisticsResponse;
import com.hrm.dto.PayrollStatisticsResponse;
import com.hrm.dto.ReportExportFile;
import com.hrm.dto.WorkStatisticsResponse;
import com.hrm.export.ReportExcelWriter;
import com.hrm.model.AuditLog;
import com.hrm.repository.AuditLogRepository;
import com.hrm.security.AuthenticatedAccount;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

@Service
@Transactional
public class ReportExportService {

    public static final String VIEW_AUTHORIZATION = "hasAnyRole('ADMIN','HR') and hasAuthority('REPORT_VIEW')";

    private final EmployeeStatisticsService employeeStatistics;
    private final ContractStatisticsService contractStatistics;
    private final WorkStatisticsService workStatistics;
    private final PayrollStatisticsService payrollStatistics;
    private final ReportExcelWriter excelWriter;
    private final AuditLogRepository auditLogs;

    public ReportExportService(
            EmployeeStatisticsService employeeStatistics,
            ContractStatisticsService contractStatistics,
            WorkStatisticsService workStatistics,
            PayrollStatisticsService payrollStatistics,
            ReportExcelWriter excelWriter,
            AuditLogRepository auditLogs) {
        this.employeeStatistics = employeeStatistics;
        this.contractStatistics = contractStatistics;
        this.workStatistics = workStatistics;
        this.payrollStatistics = payrollStatistics;
        this.excelWriter = excelWriter;
        this.auditLogs = auditLogs;
    }

    @PreAuthorize(VIEW_AUTHORIZATION)
    public ReportExportFile exportEmployees() {
        AuthenticatedAccount actor = currentActor();
        EmployeeStatisticsResponse report = employeeStatistics.getEmployeeStatistics();
        return completeExport(actor, "employees-report.xlsx", excelWriter.employees(report),
                "EMPLOYEES", "employees", "filters=none");
    }

    @PreAuthorize(VIEW_AUTHORIZATION)
    public ReportExportFile exportContracts(
            LocalDate fromDate, LocalDate toDate, LocalDate reportDate, int expiringWithinDays, Long departmentId) {
        AuthenticatedAccount actor = currentActor();
        // Match the JSON endpoint's defaults and record the effective, evaluated date.
        LocalDate evaluatedAt = reportDate == null ? LocalDate.now() : reportDate;
        ContractStatisticsResponse report = contractStatistics.summarize(
                fromDate, toDate, evaluatedAt, expiringWithinDays, departmentId);
        String filters = periodFilters(report.fromDate(), report.toDate())
                + "; reportDate=" + report.reportDate()
                + "; expiringWithinDays=" + report.expiringWithinDays()
                + "; departmentId=" + (report.departmentId() == null ? "ALL" : report.departmentId());
        return completeExport(actor, "contracts-report.xlsx", excelWriter.contracts(report),
                "CONTRACTS", "contracts", filters);
    }

    @PreAuthorize(VIEW_AUTHORIZATION)
    public ReportExportFile exportWork(LocalDate fromDate, LocalDate toDate) {
        AuthenticatedAccount actor = currentActor();
        WorkStatisticsResponse report = workStatistics.summarize(fromDate, toDate);
        return completeExport(actor, "work-report.xlsx", excelWriter.work(report),
                "WORK", "attendance_records", periodFilters(report.fromDate(), report.toDate()));
    }

    @PreAuthorize(PayrollStatisticsService.VIEW_AUTHORIZATION)
    public ReportExportFile exportPayroll(LocalDate fromDate, LocalDate toDate) {
        AuthenticatedAccount actor = currentActor();
        PayrollStatisticsResponse report = payrollStatistics.summarize(fromDate, toDate);
        return completeExport(actor, "payroll-report.xlsx", excelWriter.payroll(report),
                "PAYROLL", "payroll_batches", periodFilters(report.fromDate(), report.toDate())
                        + "; batchSelection=LATEST_FINALIZED_PER_PERIOD");
    }

    private ReportExportFile completeExport(
            AuthenticatedAccount actor, String fileName, byte[] content,
            String reportCode, String targetTable, String filters) {
        // Flush now; the service transaction commits before file bytes reach the controller.
        auditLogs.saveAndFlush(AuditLog.forReportExport(actor.accountId(), reportCode, targetTable, filters));
        return new ReportExportFile(fileName, content);
    }

    private AuthenticatedAccount currentActor() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()
                || !(authentication.getPrincipal() instanceof AuthenticatedAccount actor)
                || actor.accountId() == null) {
            throw new AccessDeniedException("Không xác định được tài khoản xuất báo cáo.");
        }
        return actor;
    }

    private String periodFilters(LocalDate fromDate, LocalDate toDate) {
        return "fromDate=" + fromDate + "; toDate=" + toDate;
    }
}
