package com.hrm.service;

import com.hrm.constant.PayrollConstants;
import com.hrm.dto.AttendanceSummaryDTO;
import com.hrm.dto.EmployeeSalaryProfileDTO;
import com.hrm.dto.PayrollEnhancementDTOs.*;
import com.hrm.enums.BatchStatus;
import com.hrm.exception.BusinessException;
import com.hrm.model.PayrollBatch;
import com.hrm.model.PayrollDetail;
import com.hrm.model.PayrollLine;
import com.hrm.model.PayrollPeriod;
import com.hrm.model.PayrollSnapshot;
import com.hrm.repository.PayrollBatchRepository;
import com.hrm.repository.PayrollDetailRepository;
import com.hrm.repository.PayrollLineRepository;
import com.hrm.repository.PayrollPeriodRepository;
import com.hrm.repository.PayrollSnapshotRepository;
import com.hrm.service.engine.PayrollCalculationEngine;
import com.hrm.service.provider.AttendanceDataProvider;
import com.hrm.service.provider.SalaryContractProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;

@Service
public class PayrollExecutionService {

    private final PayrollBatchRepository batchRepository;
    private final PayrollPeriodRepository periodRepository;
    private final PayrollDetailRepository detailRepository;
    private final PayrollLineRepository lineRepository;
    private final PayrollSnapshotRepository snapshotRepository;
    private final SalaryContractProvider salaryProvider;
    private final AttendanceDataProvider attendanceProvider;
    private final PayrollCalculationEngine calculationEngine;
    private final PayrollReadinessGuard readinessGuard;
    private final JdbcTemplate jdbc;

    // Giữ field này để tương thích với ReflectionTestUtils.setField(service, "readiness", guard) trong PayrollReadinessWiringTest
    private PayrollReadinessGuard readiness;

    @Autowired
    public PayrollExecutionService(PayrollBatchRepository batchRepository,
                                   PayrollPeriodRepository periodRepository,
                                   PayrollDetailRepository detailRepository,
                                   PayrollLineRepository lineRepository,
                                   PayrollSnapshotRepository snapshotRepository,
                                   SalaryContractProvider salaryProvider,
                                   AttendanceDataProvider attendanceProvider,
                                   PayrollCalculationEngine calculationEngine,
                                   PayrollReadinessGuard readinessGuard,
                                   JdbcTemplate jdbc) {
        this.batchRepository = batchRepository;
        this.periodRepository = periodRepository;
        this.detailRepository = detailRepository;
        this.lineRepository = lineRepository;
        this.snapshotRepository = snapshotRepository;
        this.salaryProvider = salaryProvider;
        this.attendanceProvider = attendanceProvider;
        this.calculationEngine = calculationEngine;
        this.readinessGuard = readinessGuard;
        this.readiness = readinessGuard;
        this.jdbc = jdbc;
    }

    // Constructor 8 tham số giữ tương thích với PayrollReadinessWiringTest và PayrollSyntheticGuardTest
    public PayrollExecutionService(PayrollBatchRepository batchRepository,
                                   PayrollPeriodRepository periodRepository,
                                   PayrollDetailRepository detailRepository,
                                   PayrollLineRepository lineRepository,
                                   PayrollSnapshotRepository snapshotRepository,
                                   SalaryContractProvider salaryProvider,
                                   AttendanceDataProvider attendanceProvider,
                                   PayrollCalculationEngine calculationEngine) {
        this(batchRepository, periodRepository, detailRepository, lineRepository,
                snapshotRepository, salaryProvider, attendanceProvider, calculationEngine, null, null);
    }

    @Transactional
    public String calculateBatch(Long batchId) {
        // 1. Chặn nguồn dữ liệu giả lập (Mock) trước khi truy vấn DB (đảm bảo pass PayrollSyntheticGuardTest)
        if ((salaryProvider != null && salaryProvider.isSynthetic())
                || (attendanceProvider != null && attendanceProvider.isSynthetic())) {
            throw new BusinessException(HttpStatus.CONFLICT,
                    "Nguồn tính lương đang là dữ liệu mẫu. Chưa được tính/chốt từ nguồn này; cần kết nối dữ liệu hợp đồng và công thực tế.");
        }

        PayrollBatch batch = batchRepository.findForUpdate(batchId)
                .or(() -> batchRepository.findById(batchId))
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "Không tìm thấy đợt bảng lương với ID: " + batchId));

        if (batch.getStatus() != BatchStatus.NHAP) {
            throw new BusinessException(HttpStatus.CONFLICT, "Bảng lương đã chốt hoặc công bố, không được phép tính lại trực tiếp!");
        }

        PayrollPeriod period = periodRepository.findById(batch.getPeriodId())
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "Không tìm thấy kỳ lương ID: " + batch.getPeriodId()));

        // 2. Kiểm tra điều kiện sẵn sàng trước khi xóa/tính lại snapshot
        if (this.readinessGuard != null) {
            this.readinessGuard.assertReadyForCalculate(batch, period);
        } else if (this.readiness != null) {
            this.readiness.requireReady(batch.getPeriodId());
        }

        // 3. Xóa dữ liệu tính nháp cũ nếu tính lại
        List<PayrollDetail> oldDetails = detailRepository.findByBatchId(batchId);
        if (!oldDetails.isEmpty()) {
            List<Long> detailIds = oldDetails.stream().map(PayrollDetail::getPayrollDetailId).toList();
            snapshotRepository.deleteByPayrollDetailIdIn(detailIds);
            lineRepository.deleteByPayrollDetailIdIn(detailIds);
            detailRepository.deleteAll(oldDetails);
        }

        List<EmployeeSalaryProfileDTO> employeeProfiles = salaryProvider.getAllActiveSalaryProfiles(
                period.getPeriodStart(), period.getPeriodEnd());

        int count = 0;
        for (EmployeeSalaryProfileDTO profile : employeeProfiles) {
            AttendanceSummaryDTO attendance = attendanceProvider.getAttendanceSummary(
                    profile.getEmployeeId(), period.getPeriodStart(), period.getPeriodEnd());

            EmployeePayrollWorkDetailDTO workDetail = buildEmployeeWorkDetail(
                    batchId, profile.getEmployeeId(), period.getPeriodStart(), period.getPeriodEnd(), attendance);

            BigDecimal approvedBonus = queryApprovedDecisionSum(
                    profile.getEmployeeId(), period.getPeriodStart(), period.getPeriodEnd(), "ADD");
            BigDecimal approvedDiscipline = queryApprovedDecisionSum(
                    profile.getEmployeeId(), period.getPeriodStart(), period.getPeriodEnd(), "DEDUCT");

            PayrollDetail detail = new PayrollDetail(batchId, profile.getEmployeeId());
            detail.setCreatedAt(LocalDateTime.now());
            detail = detailRepository.save(detail);

            List<PayrollLine> lines = calculationEngine.calculatePayrollLines(
                    detail.getPayrollDetailId(),
                    profile,
                    attendance,
                    workDetail.dailyRecords(),
                    workDetail.unexcusedAbsenceDays(),
                    approvedBonus,
                    approvedDiscipline
            );
            for (PayrollLine line : lines) {
                line.setCreatedAt(LocalDateTime.now());
            }
            lineRepository.saveAll(lines);

            PayrollSnapshot snapshot = new PayrollSnapshot();
            snapshot.setPayrollDetailId(detail.getPayrollDetailId());
            snapshot.setSchemaVersion(2);
            snapshot.setPolicyVersion("2026.2-PENALTY");
            snapshot.setInputSnapshotJson(buildSnapshotJson(profile, attendance, workDetail));
            snapshot.setCapturedAt(LocalDateTime.now());
            snapshot.setCreatedAt(LocalDateTime.now());
            snapshotRepository.save(snapshot);

            count++;
        }

        return "Tính lương thành công cho " + count + " nhân viên trong đợt bảng lương #" + batchId + " (" + period.getPeriodCode() + ").";
    }

    // =========================================================================
    // 1. CHI TIẾT MINH CHỨNG NGÀY CÔNG (CHECK-IN, CHECK-OUT, SỐ GIỜ LÀM)
    // =========================================================================
    public EmployeePayrollWorkDetailDTO getEmployeeWorkDetails(Long batchId, Long employeeId) {
        PayrollBatch batch = batchRepository.findById(batchId)
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "Không tìm thấy đợt lương #" + batchId));
        PayrollPeriod period = periodRepository.findById(batch.getPeriodId())
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "Không tìm thấy kỳ lương"));

        AttendanceSummaryDTO summary = attendanceProvider.getAttendanceSummary(
                employeeId, period.getPeriodStart(), period.getPeriodEnd());

        return buildEmployeeWorkDetail(batchId, employeeId, period.getPeriodStart(), period.getPeriodEnd(), summary);
    }

    private EmployeePayrollWorkDetailDTO buildEmployeeWorkDetail(
            Long batchId, Long employeeId, LocalDate periodStart, LocalDate periodEnd, AttendanceSummaryDTO summary) {

        if (jdbc == null) {
            BigDecimal std = summary.getStandardWorkDays() != null
                    ? summary.getStandardWorkDays()
                    : BigDecimal.valueOf(PayrollConstants.DEFAULT_STANDARD_WORK_DAYS);
            return new EmployeePayrollWorkDetailDTO(
                    batchId, employeeId, "NV-" + employeeId, "Nhân viên #" + employeeId, "",
                    std,
                    summary.getActualWorkDays() != null ? summary.getActualWorkDays() : BigDecimal.ZERO,
                    BigDecimal.ZERO, 0L, BigDecimal.ZERO, 0, BigDecimal.ZERO,
                    summary.getPaidLeaveDays() != null ? summary.getPaidLeaveDays() : BigDecimal.ZERO,
                    summary.getOvertimeHours() != null ? summary.getOvertimeHours() : BigDecimal.ZERO,
                    List.of()
            );
        }

        Map<String, Object> empInfo = jdbc.queryForMap("""
            SELECT e.employee_code, e.full_name,
                   COALESCE(d.department_name, N'Chưa phân công') AS department_name
            FROM dbo.employees e
            LEFT JOIN dbo.employee_assignments a ON a.employee_id = e.employee_id
                 AND a.effective_from <= ? AND (a.effective_to IS NULL OR a.effective_to > ?)
            LEFT JOIN dbo.departments d ON d.department_id = a.department_id
            WHERE e.employee_id = ?
        """, periodEnd, periodStart, employeeId);

        List<LocalDate[]> paidLeaveRanges = new ArrayList<>();
        jdbc.query("""
            SELECT CAST(ld.leave_start AS date) AS l_start, CAST(ld.leave_end AS date) AS l_end
            FROM dbo.employee_requests r
            JOIN dbo.leave_request_details ld ON ld.request_id = r.request_id
            JOIN dbo.leave_types lt ON lt.leave_type_id = ld.leave_type_id
            WHERE r.employee_id = ?
              AND r.status = 'DA_DUYET'
              AND lt.is_paid = 1
              AND ld.leave_start < ? AND ld.leave_end >= ?
        """, rs -> {
            paidLeaveRanges.add(new LocalDate[]{
                    rs.getDate("l_start").toLocalDate(),
                    rs.getDate("l_end").toLocalDate()
            });
        }, employeeId, periodEnd.plusDays(1).atStartOfDay(), periodStart.atStartOfDay());

        List<DailyWorkEvidenceDTO> dailyRecords = new ArrayList<>();
        final long[] totalLateMinsHolder = {0L};
        final int[] forfeitedLunchHolder = {0};
        final BigDecimal[] totalWorkedHrsHolder = {BigDecimal.ZERO};
        final BigDecimal[] unexcusedDaysHolder = {BigDecimal.ZERO};

        String sql = """
            SELECT ws.schedule_id, ws.work_date, ws.planned_start, ws.planned_end, ws.planned_break_minutes,
                   COALESCE(sh.shift_name, N'Ca tiêu chuẩn') AS shift_name,
                   ar.attendance_id,
                   COALESCE(ar.check_in_reviewed_value, ar.actual_start) AS eff_start,
                   COALESCE(ar.check_out_reviewed_value, ar.actual_end) AS eff_end,
                   COALESCE(ar.actual_break_minutes, ws.planned_break_minutes) AS break_mins,
                   ar.attendance_result,
                   COALESCE(ar.verification_status, 'CHUA_XAC_NHAN') AS verification_status
            FROM dbo.work_schedules ws
            LEFT JOIN dbo.work_shifts sh ON sh.shift_id = ws.shift_id
            LEFT JOIN dbo.attendance_records ar ON ar.schedule_id = ws.schedule_id AND ar.invalidated_at IS NULL
            WHERE ws.employee_id = ?
              AND ws.cancelled_at IS NULL
              AND ws.work_date BETWEEN ? AND ?
            ORDER BY ws.work_date ASC, ws.planned_start ASC
        """;

        jdbc.query(sql, rs -> {
            Long scheduleId = rs.getLong("schedule_id");
            long attIdRaw = rs.getLong("attendance_id");
            Long attendanceId = rs.wasNull() ? null : attIdRaw;

            LocalDate workDate = rs.getDate("work_date").toLocalDate();
            String shiftName = rs.getString("shift_name");
            LocalDateTime plannedStart = rs.getTimestamp("planned_start").toLocalDateTime();
            LocalDateTime plannedEnd = rs.getTimestamp("planned_end").toLocalDateTime();
            int plannedBreak = rs.getInt("planned_break_minutes");

            Timestamp tsStart = rs.getTimestamp("eff_start");
            Timestamp tsEnd = rs.getTimestamp("eff_end");
            LocalDateTime actualStart = tsStart != null ? tsStart.toLocalDateTime() : null;
            LocalDateTime actualEnd = tsEnd != null ? tsEnd.toLocalDateTime() : null;
            int breakMins = rs.getInt("break_mins");
            String rawResult = rs.getString("attendance_result");
            String verStatus = rs.getString("verification_status");

            long plannedNetMins = Math.max(0, ChronoUnit.MINUTES.between(plannedStart, plannedEnd) - plannedBreak);
            BigDecimal plannedHours = BigDecimal.valueOf(plannedNetMins)
                    .divide(BigDecimal.valueOf(60), 2, RoundingMode.HALF_UP);

            boolean isPaidLeave = paidLeaveRanges.stream()
                    .anyMatch(rng -> !workDate.isBefore(rng[0]) && !workDate.isAfter(rng[1]));

            BigDecimal workedHours = BigDecimal.ZERO;
            long lateMinutes = 0;
            long earlyLeaveMinutes = 0;
            boolean lunchForfeited = false;
            String displayResult;
            String note;

            if ("DI_LAM".equals(rawResult) && actualStart != null) {
                displayResult = "DI_LAM";
                if (actualStart.isAfter(plannedStart)) {
                    lateMinutes = ChronoUnit.MINUTES.between(plannedStart, actualStart);
                }
                if (actualEnd != null && actualEnd.isBefore(plannedEnd)) {
                    earlyLeaveMinutes = ChronoUnit.MINUTES.between(actualEnd, plannedEnd);
                }
                if (actualEnd != null) {
                    long actualNetMins = Math.max(0, ChronoUnit.MINUTES.between(actualStart, actualEnd) - breakMins);
                    workedHours = BigDecimal.valueOf(actualNetMins)
                            .divide(BigDecimal.valueOf(60), 2, RoundingMode.HALF_UP);
                }

                // Quy tắc: Trễ quá 1/2 ngày làm việc -> trừ phụ cấp cơm trưa ngày đó
                if (plannedNetMins > 0 && lateMinutes > (plannedNetMins / 2)) {
                    lunchForfeited = true;
                    forfeitedLunchHolder[0]++;
                }

                totalLateMinsHolder[0] += lateMinutes;
                totalWorkedHrsHolder[0] = totalWorkedHrsHolder[0].add(workedHours);

                if (lunchForfeited) {
                    note = String.format("Đi trễ %d phút (> 1/2 ca %d phút) — Trừ giờ công trễ & cắt phụ cấp cơm trưa ngày này",
                            lateMinutes, plannedNetMins);
                } else if (lateMinutes > 0) {
                    BigDecimal lateHrs = BigDecimal.valueOf(lateMinutes).divide(BigDecimal.valueOf(60), 2, RoundingMode.HALF_UP);
                    note = String.format("Đi trễ %d phút (%s giờ) — Trừ %s giờ lương", lateMinutes, lateHrs, lateHrs);
                } else if (actualEnd == null) {
                    note = "Thiếu dữ liệu check-out";
                } else {
                    note = "Đúng giờ, đủ minh chứng vào/ra";
                }
            } else if (isPaidLeave) {
                displayResult = "NGHI_PHEP_CO_LUONG";
                workedHours = plannedHours;
                note = "Nghỉ phép có hưởng lương đã được duyệt";
            } else if ("VANG".equals(rawResult) || (rawResult == null && workDate.isBefore(LocalDate.now()))) {
                displayResult = "VANG_KHONG_PHEP";
                unexcusedDaysHolder[0] = unexcusedDaysHolder[0].add(BigDecimal.ONE);
                note = "Vắng mặt không phép — Khấu trừ 1 ngày công";
            } else {
                displayResult = "CHUA_CHAM_CONG";
                note = "Chưa phát sinh dữ liệu chấm công";
            }

            dailyRecords.add(new DailyWorkEvidenceDTO(
                    scheduleId, attendanceId, workDate, shiftName,
                    plannedStart, plannedEnd, actualStart, actualEnd,
                    breakMins, plannedHours, workedHours,
                    lateMinutes, earlyLeaveMinutes, lunchForfeited,
                    displayResult, verStatus, note
            ));
        }, employeeId, periodStart, periodEnd);

        BigDecimal standardDays = summary.getStandardWorkDays() != null
                ? summary.getStandardWorkDays()
                : BigDecimal.valueOf(PayrollConstants.DEFAULT_STANDARD_WORK_DAYS);

        return new EmployeePayrollWorkDetailDTO(
                batchId,
                employeeId,
                String.valueOf(empInfo.get("employee_code")),
                String.valueOf(empInfo.get("full_name")),
                String.valueOf(empInfo.get("department_name")),
                standardDays,
                summary.getActualWorkDays() != null ? summary.getActualWorkDays() : BigDecimal.ZERO,
                totalWorkedHrsHolder[0],
                totalLateMinsHolder[0],
                BigDecimal.valueOf(totalLateMinsHolder[0]).divide(BigDecimal.valueOf(60), 2, RoundingMode.HALF_UP),
                forfeitedLunchHolder[0],
                unexcusedDaysHolder[0],
                summary.getPaidLeaveDays() != null ? summary.getPaidLeaveDays() : BigDecimal.ZERO,
                summary.getOvertimeHours() != null ? summary.getOvertimeHours() : BigDecimal.ZERO,
                dailyRecords
        );
    }

    // =========================================================================
    // 2. XEM DANH SÁCH PHỤ CẤP CỦA THÁNG & CỦA PHÒNG BAN
    // =========================================================================
    public PeriodAllowanceOverviewDTO getPeriodAllowances(Long periodId, Long departmentId) {
        PayrollPeriod period = periodRepository.findById(periodId)
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "Không tìm thấy kỳ lương #" + periodId));

        String sql = """
            SELECT ea.employee_allowance_id, ea.employee_id, e.employee_code, e.full_name,
                   d.department_id, COALESCE(d.department_name, N'Chưa phân công') AS department_name,
                   atp.type_code, atp.type_name, ea.calculation_basis, ea.amount_type, ea.amount,
                   ea.effective_from, ea.effective_to, atp.is_taxable, atp.counts_for_social_insurance,
                   ea.condition_note,
                   COALESCE((
                       SELECT TOP 1 st.base_salary
                       FROM dbo.contracts c
                       JOIN dbo.contract_salary_terms st ON st.contract_id = c.contract_id
                       WHERE c.employee_id = e.employee_id AND c.status = 'DA_KY'
                         AND st.effective_from <= ? AND (st.effective_to IS NULL OR st.effective_to >= ?)
                       ORDER BY st.effective_from DESC
                   ), 0) AS base_salary
            FROM dbo.employee_allowances ea
            JOIN dbo.allowance_types atp ON atp.allowance_type_id = ea.allowance_type_id
            JOIN dbo.employees e ON e.employee_id = ea.employee_id
            LEFT JOIN dbo.employee_assignments a ON a.employee_id = e.employee_id
                 AND a.effective_from <= ? AND (a.effective_to IS NULL OR a.effective_to > ?)
            LEFT JOIN dbo.departments d ON d.department_id = a.department_id
            WHERE ea.effective_from <= ?
              AND (ea.effective_to IS NULL OR ea.effective_to >= ?)
              AND e.employment_status = 'DANG_LAM'
            ORDER BY d.department_name, e.employee_code, atp.type_name
        """;

        List<PeriodAllowanceItemDTO> allItems = new ArrayList<>();
        Map<Long, DepartmentAccumulator> deptMap = new LinkedHashMap<>();
        final BigDecimal[] companyTotal = {BigDecimal.ZERO};

        jdbc.query(sql, rs -> {
            Long deptIdObj = rs.getObject("department_id") != null ? rs.getLong("department_id") : 0L;
            String deptName = rs.getString("department_name");
            BigDecimal cfgAmt = rs.getBigDecimal("amount");
            String amountType = rs.getString("amount_type");
            BigDecimal baseSalary = rs.getBigDecimal("base_salary");

            BigDecimal estimated = "PERCENT_BASE_SALARY".equalsIgnoreCase(amountType)
                    ? baseSalary.multiply(cfgAmt).divide(BigDecimal.valueOf(100), 0, RoundingMode.HALF_UP)
                    : cfgAmt.setScale(0, RoundingMode.HALF_UP);

            PeriodAllowanceItemDTO item = new PeriodAllowanceItemDTO(
                    rs.getLong("employee_allowance_id"),
                    rs.getLong("employee_id"),
                    rs.getString("employee_code"),
                    rs.getString("full_name"),
                    deptIdObj,
                    deptName,
                    rs.getString("type_code"),
                    rs.getString("type_name"),
                    rs.getString("calculation_basis"),
                    amountType,
                    cfgAmt,
                    estimated,
                    rs.getDate("effective_from").toLocalDate(),
                    rs.getDate("effective_to") != null ? rs.getDate("effective_to").toLocalDate() : null,
                    rs.getBoolean("is_taxable"),
                    rs.getBoolean("counts_for_social_insurance"),
                    rs.getString("condition_note")
            );

            DepartmentAccumulator acc = deptMap.computeIfAbsent(deptIdObj,
                    k -> new DepartmentAccumulator(deptIdObj, "PB-" + deptIdObj, deptName));
            acc.employeeIds.add(item.employeeId());
            acc.allowanceCount++;
            acc.totalAmount = acc.totalAmount.add(estimated);
            acc.byType.merge(item.allowanceName(), estimated, BigDecimal::add);

            companyTotal[0] = companyTotal[0].add(estimated);

            if (departmentId == null || Objects.equals(departmentId, deptIdObj)) {
                allItems.add(item);
            }
        }, period.getPeriodEnd(), period.getPeriodStart(),
           period.getPeriodEnd(), period.getPeriodStart(),
           period.getPeriodEnd(), period.getPeriodStart());

        List<DepartmentAllowanceSummaryDTO> deptSummaries = deptMap.values().stream()
                .map(a -> new DepartmentAllowanceSummaryDTO(
                        a.departmentId, a.departmentCode, a.departmentName,
                        a.employeeIds.size(), a.allowanceCount, a.totalAmount, a.byType
                )).toList();

        return new PeriodAllowanceOverviewDTO(
                period.getPeriodId(),
                period.getPeriodCode(),
                period.getPeriodStart(),
                period.getPeriodEnd(),
                companyTotal[0],
                deptSummaries,
                allItems
        );
    }

    // =========================================================================
    // 4A. BỘ LỌC & LỊCH SỬ LƯƠNG NHÂN VIÊN QUA CÁC KỲ
    // =========================================================================
    public List<EmployeePayrollHistoryItemDTO> getEmployeePayrollHistory(
            Long employeeId, Long departmentId, String keyword, String status) {

        StringBuilder sql = new StringBuilder("""
            SELECT pd.payroll_detail_id, pb.batch_id, pp.period_id, pp.period_code, pp.period_start, pp.period_end,
                   pb.version_no, pb.status AS batch_status, pb.published_at,
                   e.employee_id, e.employee_code, e.full_name,
                   d.department_id, COALESCE(d.department_name, N'Chưa phân công') AS department_name,
                   COALESCE(pos.position_name, N'Nhân viên') AS position_name,
                   COALESCE((SELECT SUM(pl.amount) FROM dbo.payroll_lines pl
                             WHERE pl.payroll_detail_id = pd.payroll_detail_id AND pl.direction_snapshot = 'ADD'), 0) AS total_gross,
                   COALESCE((SELECT SUM(pl.amount) FROM dbo.payroll_lines pl
                             WHERE pl.payroll_detail_id = pd.payroll_detail_id AND pl.direction_snapshot = 'DEDUCT'), 0) AS total_deduct,
                   ps.payslip_id
            FROM dbo.payroll_details pd
            JOIN dbo.payroll_batches pb ON pb.batch_id = pd.batch_id
            JOIN dbo.payroll_periods pp ON pp.period_id = pb.period_id
            JOIN dbo.employees e ON e.employee_id = pd.employee_id
            LEFT JOIN dbo.employee_assignments a ON a.employee_id = e.employee_id
                 AND a.effective_from <= pp.period_end AND (a.effective_to IS NULL OR a.effective_to > pp.period_start)
            LEFT JOIN dbo.departments d ON d.department_id = a.department_id
            LEFT JOIN dbo.positions pos ON pos.position_id = a.position_id
            LEFT JOIN dbo.payslips ps ON ps.payroll_detail_id = pd.payroll_detail_id
            WHERE 1 = 1
        """);

        List<Object> params = new ArrayList<>();
        if (employeeId != null) {
            sql.append(" AND e.employee_id = ?");
            params.add(employeeId);
        }
        if (departmentId != null) {
            sql.append(" AND d.department_id = ?");
            params.add(departmentId);
        }
        if (keyword != null && !keyword.isBlank()) {
            sql.append(" AND (e.employee_code LIKE ? OR e.full_name LIKE ?)");
            String kw = "%" + keyword.trim() + "%";
            params.add(kw);
            params.add(kw);
        }
        if (status != null && !status.isBlank()) {
            if ("CONG_BO".equalsIgnoreCase(status)) {
                sql.append(" AND pb.published_at IS NOT NULL");
            } else {
                sql.append(" AND pb.status = ?");
                params.add(status);
            }
        }
        sql.append(" ORDER BY pp.period_start DESC, pb.version_no DESC, e.employee_code ASC");

        return jdbc.query(sql.toString(), (rs, rowNum) -> {
            BigDecimal gross = rs.getBigDecimal("total_gross");
            BigDecimal deduct = rs.getBigDecimal("total_deduct");
            BigDecimal net = gross.subtract(deduct);
            boolean published = rs.getTimestamp("published_at") != null;
            String rawStatus = rs.getString("batch_status");
            String effectiveStatus = published ? "CONG_BO" : rawStatus;

            return new EmployeePayrollHistoryItemDTO(
                    rs.getLong("payroll_detail_id"),
                    rs.getLong("batch_id"),
                    rs.getLong("period_id"),
                    rs.getString("period_code"),
                    rs.getDate("period_start").toLocalDate(),
                    rs.getDate("period_end").toLocalDate(),
                    rs.getInt("version_no"),
                    effectiveStatus,
                    published,
                    rs.getLong("employee_id"),
                    rs.getString("employee_code"),
                    rs.getString("full_name"),
                    rs.getObject("department_id") != null ? rs.getLong("department_id") : null,
                    rs.getString("department_name"),
                    rs.getString("position_name"),
                    BigDecimal.ZERO,
                    BigDecimal.ZERO,
                    gross,
                    deduct,
                    net,
                    rs.getObject("payslip_id") != null ? rs.getLong("payslip_id") : null
            );
        }, params.toArray());
    }

    // =========================================================================
    // 4B. THỐNG KÊ CHI PHÍ TIỀN LƯƠNG THEO PHÒNG BAN
    // =========================================================================
    public List<DepartmentPayrollCostDTO> getDepartmentCostSummary(Long periodId) {
        PayrollPeriod period = periodRepository.findById(periodId)
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "Không tìm thấy kỳ lương #" + periodId));

        Long latestBatchId = jdbc.query(
                "SELECT TOP 1 batch_id FROM dbo.payroll_batches WHERE period_id = ? ORDER BY version_no DESC",
                rs -> rs.next() ? rs.getLong("batch_id") : null,
                periodId
        );
        if (latestBatchId == null) {
            return List.of();
        }

        String sql = """
            SELECT COALESCE(d.department_id, 0) AS dept_id,
                   COALESCE(d.department_code, 'KHAC') AS dept_code,
                   COALESCE(d.department_name, N'Chưa phân bổ phòng ban') AS dept_name,
                   COUNT(DISTINCT pd.employee_id) AS emp_count,
                   COALESCE(SUM(CASE WHEN pl.component_code_snapshot = 'LUONG_CONG' THEN pl.amount ELSE 0 END), 0) AS work_sal,
                   COALESCE(SUM(CASE WHEN pl.component_code_snapshot = 'TANG_CA' THEN pl.amount ELSE 0 END), 0) AS ot_pay,
                   COALESCE(SUM(CASE WHEN pl.component_code_snapshot = 'PHU_CAP' THEN pl.amount ELSE 0 END), 0) AS allow_pay,
                   COALESCE(SUM(CASE WHEN pl.component_code_snapshot = 'THUONG' THEN pl.amount ELSE 0 END), 0) AS bonus_pay,
                   COALESCE(SUM(CASE WHEN pl.direction_snapshot = 'ADD' THEN pl.amount ELSE 0 END), 0) AS gross_pay,
                   COALESCE(SUM(CASE WHEN pl.direction_snapshot = 'DEDUCT' THEN pl.amount ELSE 0 END), 0) AS deduct_pay
            FROM dbo.payroll_details pd
            LEFT JOIN dbo.payroll_lines pl ON pl.payroll_detail_id = pd.payroll_detail_id
            LEFT JOIN dbo.employee_assignments a ON a.employee_id = pd.employee_id
                 AND a.effective_from <= ? AND (a.effective_to IS NULL OR a.effective_to > ?)
            LEFT JOIN dbo.departments d ON d.department_id = a.department_id
            WHERE pd.batch_id = ?
            GROUP BY d.department_id, d.department_code, d.department_name
            ORDER BY gross_pay DESC
        """;

        List<DepartmentPayrollCostDTO> rawList = jdbc.query(sql, (rs, rowNum) -> {
            BigDecimal gross = rs.getBigDecimal("gross_pay");
            BigDecimal deduct = rs.getBigDecimal("deduct_pay");
            return new DepartmentPayrollCostDTO(
                    rs.getLong("dept_id"),
                    rs.getString("dept_code"),
                    rs.getString("dept_name"),
                    rs.getInt("emp_count"),
                    rs.getBigDecimal("work_sal"),
                    rs.getBigDecimal("ot_pay"),
                    rs.getBigDecimal("allow_pay"),
                    rs.getBigDecimal("bonus_pay"),
                    gross,
                    deduct,
                    gross.subtract(deduct),
                    BigDecimal.ZERO
            );
        }, period.getPeriodEnd(), period.getPeriodStart(), latestBatchId);

        BigDecimal totalAllGross = rawList.stream()
                .map(DepartmentPayrollCostDTO::totalGross)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        if (totalAllGross.compareTo(BigDecimal.ZERO) <= 0) {
            return rawList;
        }

        return rawList.stream().map(item -> {
            BigDecimal pct = item.totalGross()
                    .multiply(BigDecimal.valueOf(100))
                    .divide(totalAllGross, 1, RoundingMode.HALF_UP);
            return new DepartmentPayrollCostDTO(
                    item.departmentId(), item.departmentCode(), item.departmentName(), item.employeeCount(),
                    item.totalWorkSalary(), item.totalOvertimePay(), item.totalAllowance(), item.totalBonus(),
                    item.totalGross(), item.totalDeduction(), item.totalNet(), pct
            );
        }).toList();
    }

    private BigDecimal queryApprovedDecisionSum(Long employeeId, LocalDate start, LocalDate end, String effect) {
        if (jdbc == null) {
            return BigDecimal.ZERO;
        }
        BigDecimal sum = jdbc.queryForObject("""
            SELECT COALESCE(SUM(amount), 0)
            FROM dbo.employee_decisions
            WHERE employee_id = ?
              AND approval_status = 'DA_DUYET'
              AND payroll_effect = ?
              AND apply_date BETWEEN ? AND ?
        """, BigDecimal.class, employeeId, effect, start, end);
        return sum != null ? sum : BigDecimal.ZERO;
    }

    private String buildSnapshotJson(EmployeeSalaryProfileDTO profile, AttendanceSummaryDTO attendance, EmployeePayrollWorkDetailDTO workDetail) {
        return "{"
                + "\"employeeId\":" + profile.getEmployeeId() + ","
                + "\"employeeCode\":\"" + (profile.getEmployeeCode() != null ? profile.getEmployeeCode() : "") + "\","
                + "\"fullName\":\"" + (profile.getFullName() != null ? profile.getFullName() : "") + "\","
                + "\"baseSalary\":" + (profile.getBaseSalary() != null ? profile.getBaseSalary().toPlainString() : "0") + ","
                + "\"standardWorkDays\":" + workDetail.standardWorkDays().toPlainString() + ","
                + "\"actualWorkDays\":" + workDetail.actualWorkDays().toPlainString() + ","
                + "\"totalWorkedHours\":" + workDetail.totalWorkedHours().toPlainString() + ","
                + "\"totalLateMinutes\":" + workDetail.totalLateMinutes() + ","
                + "\"forfeitedLunchDays\":" + workDetail.forfeitedLunchDays() + ","
                + "\"unexcusedAbsenceDays\":" + workDetail.unexcusedAbsenceDays().toPlainString() + ","
                + "\"overtimeHours\":" + workDetail.overtimeHours().toPlainString() + ","
                + "\"calculatedAt\":\"" + LocalDateTime.now() + "\""
                + "}";
    }

    private static class DepartmentAccumulator {
        final Long departmentId;
        final String departmentCode;
        final String departmentName;
        final Set<Long> employeeIds = new HashSet<>();
        int allowanceCount = 0;
        BigDecimal totalAmount = BigDecimal.ZERO;
        final Map<String, BigDecimal> byType = new LinkedHashMap<>();

        DepartmentAccumulator(Long departmentId, String departmentCode, String departmentName) {
            this.departmentId = departmentId;
            this.departmentCode = departmentCode;
            this.departmentName = departmentName;
        }
    }
}