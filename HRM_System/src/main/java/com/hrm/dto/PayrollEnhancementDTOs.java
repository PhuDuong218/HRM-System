package com.hrm.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public final class PayrollEnhancementDTOs {

    private PayrollEnhancementDTOs() {}

    // --- 1. MINH CHỨNG NGÀY CÔNG CHI TIẾT CỦA NHÂN VIÊN ---
    public record DailyWorkEvidenceDTO(
            Long scheduleId,
            Long attendanceId,
            LocalDate workDate,
            String shiftName,
            LocalDateTime plannedStart,
            LocalDateTime plannedEnd,
            LocalDateTime actualStart,
            LocalDateTime actualEnd,
            int actualBreakMinutes,
            BigDecimal plannedHours,
            BigDecimal workedHours,
            long lateMinutes,
            long earlyLeaveMinutes,
            boolean lunchAllowanceForfeited,
            String attendanceResult,
            String verificationStatus,
            String evidenceNote
    ) {}

    public record EmployeePayrollWorkDetailDTO(
            Long batchId,
            Long employeeId,
            String employeeCode,
            String fullName,
            String departmentName,
            BigDecimal standardWorkDays,
            BigDecimal actualWorkDays,
            BigDecimal totalWorkedHours,
            long totalLateMinutes,
            BigDecimal totalLateHours,
            int forfeitedLunchDays,
            BigDecimal unexcusedAbsenceDays,
            BigDecimal paidLeaveDays,
            BigDecimal overtimeHours,
            List<DailyWorkEvidenceDTO> dailyRecords
    ) {}

    // --- 2. PHỤ CẤP THEO THÁNG & PHÒNG BAN ---
    public record PeriodAllowanceItemDTO(
            Long employeeAllowanceId,
            Long employeeId,
            String employeeCode,
            String fullName,
            Long departmentId,
            String departmentName,
            String allowanceCode,
            String allowanceName,
            String calculationBasis,
            String amountType,
            BigDecimal configuredAmount,
            BigDecimal estimatedPeriodAmount,
            LocalDate effectiveFrom,
            LocalDate effectiveTo,
            boolean isTaxable,
            boolean countsForSocialInsurance,
            String conditionNote
    ) {}

    public record DepartmentAllowanceSummaryDTO(
            Long departmentId,
            String departmentCode,
            String departmentName,
            int employeeCount,
            int allowanceCount,
            BigDecimal totalAllowanceAmount,
            Map<String, BigDecimal> amountByAllowanceType
    ) {}

    public record PeriodAllowanceOverviewDTO(
            Long periodId,
            String periodCode,
            LocalDate periodStart,
            LocalDate periodEnd,
            BigDecimal totalCompanyAllowance,
            List<DepartmentAllowanceSummaryDTO> departmentSummaries,
            List<PeriodAllowanceItemDTO> items
    ) {}

    // --- 4. LỊCH SỬ LƯƠNG NHÂN VIÊN & CHI PHÍ THEO PHÒNG BAN ---
    public record EmployeePayrollHistoryItemDTO(
            Long payrollDetailId,
            Long batchId,
            Long periodId,
            String periodCode,
            LocalDate periodStart,
            LocalDate periodEnd,
            int versionNo,
            String batchStatus,
            boolean published,
            Long employeeId,
            String employeeCode,
            String fullName,
            Long departmentId,
            String departmentName,
            String positionName,
            BigDecimal actualWorkDays,
            BigDecimal overtimeHours,
            BigDecimal totalGross,
            BigDecimal totalDeduction,
            BigDecimal netSalary,
            Long payslipId
    ) {}

    public record DepartmentPayrollCostDTO(
            Long departmentId,
            String departmentCode,
            String departmentName,
            int employeeCount,
            BigDecimal totalWorkSalary,
            BigDecimal totalOvertimePay,
            BigDecimal totalAllowance,
            BigDecimal totalBonus,
            BigDecimal totalGross,
            BigDecimal totalDeduction,
            BigDecimal totalNet,
            BigDecimal costSharePercent
    ) {}

    // --- 5. CHI TIẾT LÝ DO CHẶN TÍNH LƯƠNG / CHỐT LƯƠNG ---
    public record PayrollBlockerItemDTO(
            String category,
            String categoryLabel,
            Long employeeId,
            String employeeCode,
            String fullName,
            String departmentName,
            String referenceCode,
            String dateInfo,
            String detail,
            String actionRoute
    ) {}

    public record PayrollReadinessReportDTO(
            Long batchId,
            Long periodId,
            String periodCode,
            boolean readyForCalculate,
            boolean readyForLock,
            String summaryMessage,
            List<PayrollBlockerItemDTO> blockers
    ) {}
}