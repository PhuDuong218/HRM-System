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
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;

@Service
public class PayrollExecutionService {

    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm");

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
        return calculateBatch(batchId, false);
    }

    @Transactional
    public String calculateBatch(Long batchId, boolean forceDemo) {
        // 1. Khi chạy unit test không có JdbcTemplate (PayrollSyntheticGuardTest), chặn nguồn dữ liệu giả lập (Mock)
        if (jdbc == null && ((salaryProvider != null && salaryProvider.isSynthetic())
                || (attendanceProvider != null && attendanceProvider.isSynthetic()))) {
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

        // 2. Kiểm tra điều kiện sẵn sàng trước khi xóa/tính lại snapshot (trừ khi bật chế độ forceDemo để trình diễn)
        if (!forceDemo) {
            if (this.readinessGuard != null) {
                this.readinessGuard.assertReadyForCalculate(batch, period);
            } else if (this.readiness != null) {
                this.readiness.requireReady(batch.getPeriodId());
            }
        }

        ensureSalaryComponentCatalog();

        // 3. Xóa dữ liệu tính nháp cũ nếu tính lại
        List<PayrollDetail> oldDetails = detailRepository.findByBatchId(batchId);
        if (!oldDetails.isEmpty()) {
            List<Long> detailIds = oldDetails.stream().map(PayrollDetail::getPayrollDetailId).toList();
            snapshotRepository.deleteByPayrollDetailIdIn(detailIds);
            lineRepository.deleteByPayrollDetailIdIn(detailIds);
            detailRepository.deleteAll(oldDetails);
            detailRepository.flush();
        }

        List<EmployeeSalaryProfileDTO> employeeProfiles = (jdbc != null)
                ? loadDatabaseSalaryProfiles(period.getPeriodStart(), period.getPeriodEnd())
                : salaryProvider.getAllActiveSalaryProfiles(period.getPeriodStart(), period.getPeriodEnd());

        int count = 0;
        for (EmployeeSalaryProfileDTO profile : employeeProfiles) {
            AttendanceSummaryDTO baseAttendance = (jdbc != null)
                    ? new AttendanceSummaryDTO(profile.getEmployeeId(),
                        BigDecimal.valueOf(PayrollConstants.DEFAULT_STANDARD_WORK_DAYS),
                        BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO)
                    : attendanceProvider.getAttendanceSummary(profile.getEmployeeId(), period.getPeriodStart(), period.getPeriodEnd());

            EmployeePayrollWorkDetailDTO workDetail = buildEmployeeWorkDetail(
                    batchId, profile.getEmployeeId(), period.getPeriodStart(), period.getPeriodEnd(), baseAttendance);

            AttendanceSummaryDTO effectiveAttendance = new AttendanceSummaryDTO(
                    profile.getEmployeeId(),
                    workDetail.standardWorkDays(),
                    workDetail.actualWorkDays(),
                    workDetail.paidLeaveDays(),
                    workDetail.unexcusedAbsenceDays(),
                    workDetail.overtimeHours()
            );

            DecisionSummary bonusDecision = queryApprovedDecisionSummary(
                    profile.getEmployeeId(), period.getPeriodStart(), period.getPeriodEnd(), "ADD");
            DecisionSummary disciplineDecision = queryApprovedDecisionSummary(
                    profile.getEmployeeId(), period.getPeriodStart(), period.getPeriodEnd(), "DEDUCT");

            PayrollDetail detail = new PayrollDetail(batchId, profile.getEmployeeId());
            detail.setCreatedAt(LocalDateTime.now());
            detail = detailRepository.save(detail);

            List<PayrollLine> lines = calculationEngine.calculatePayrollLines(
                    detail.getPayrollDetailId(),
                    profile,
                    effectiveAttendance,
                    workDetail.dailyRecords(),
                    workDetail.unexcusedAbsenceDays(),
                    bonusDecision.totalAmount(),
                    disciplineDecision.totalAmount(),
                    bonusDecision.explanation(),
                    disciplineDecision.explanation()
            );
            for (PayrollLine line : lines) {
                line.setCreatedAt(LocalDateTime.now());
            }
            lineRepository.saveAll(lines);

            PayrollSnapshot snapshot = new PayrollSnapshot();
            snapshot.setPayrollDetailId(detail.getPayrollDetailId());
            snapshot.setSchemaVersion(2);
            snapshot.setPolicyVersion("2026.2-PENALTY");
            snapshot.setInputSnapshotJson(buildSnapshotJson(profile, effectiveAttendance, workDetail));
            snapshot.setCapturedAt(LocalDateTime.now());
            snapshot.setCreatedAt(LocalDateTime.now());
            snapshotRepository.save(snapshot);

            count++;
        }

        String modeNote = forceDemo ? " (Chế độ Demo: đã bỏ qua các đơn chờ duyệt để chạy thử nghiệm)" : "";
        return "Đã tính lương tự động thành công cho " + count + " nhân viên trong đợt #" + batchId
                + " (Kỳ " + period.getPeriodCode() + ")" + modeNote + ".";
    }

    private void ensureSalaryComponentCatalog() {
        if (jdbc == null) {
            return;
        }
        jdbc.update("""
            IF NOT EXISTS (SELECT 1 FROM dbo.salary_components WHERE component_code = 'THUONG')
                INSERT INTO dbo.salary_components(component_code, component_name, direction, is_active, created_at)
                VALUES ('THUONG', N'Tiền thưởng', 'ADD', 1, SYSUTCDATETIME());
            IF NOT EXISTS (SELECT 1 FROM dbo.salary_components WHERE component_code = 'KHAU_TRU')
                INSERT INTO dbo.salary_components(component_code, component_name, direction, is_active, created_at)
                VALUES ('KHAU_TRU', N'Khấu trừ / Phạt', 'DEDUCT', 1, SYSUTCDATETIME());
        """);
    }

    private List<EmployeeSalaryProfileDTO> loadDatabaseSalaryProfiles(LocalDate periodStart, LocalDate periodEnd) {
        String sql = """
            SELECT e.employee_id, e.employee_code, e.full_name,
                   st.base_salary
            FROM dbo.employees e
            CROSS APPLY (
                SELECT TOP 1 t.base_salary
                FROM dbo.contracts c
                JOIN dbo.contract_salary_terms t ON t.contract_id = c.contract_id
                WHERE c.employee_id = e.employee_id
                  AND c.status IN ('DA_KY', 'DA_KET_THUC', 'HET_HAN')
                  AND c.start_date <= ?
                  AND (c.end_date IS NULL OR c.end_date >= ?)
                  AND t.effective_from <= ?
                  AND (t.effective_to IS NULL OR t.effective_to >= ?)
                ORDER BY t.effective_from DESC, t.salary_term_id DESC
            ) st
            WHERE e.employment_status = 'DANG_LAM'
            ORDER BY e.employee_code ASC
        """;

        List<EmployeeSalaryProfileDTO> profiles = jdbc.query(sql, (rs, rowNum) -> {
            EmployeeSalaryProfileDTO p = new EmployeeSalaryProfileDTO();
            p.setEmployeeId(rs.getLong("employee_id"));
            p.setEmployeeCode(rs.getString("employee_code"));
            p.setFullName(rs.getString("full_name"));
            p.setBaseSalary(rs.getBigDecimal("base_salary"));
            p.setAllowances(new ArrayList<>());
            return p;
        }, periodEnd, periodStart, periodEnd, periodStart);

        if (profiles.isEmpty()) {
            return profiles;
        }

        Map<Long, EmployeeSalaryProfileDTO> profileByEmp = new LinkedHashMap<>();
        for (EmployeeSalaryProfileDTO p : profiles) {
            profileByEmp.put(p.getEmployeeId(), p);
        }

        String allowanceSql = """
            SELECT ea.employee_id, atp.type_code, atp.type_name, ea.calculation_basis, ea.amount_type, ea.amount
            FROM dbo.employee_allowances ea
            JOIN dbo.allowance_types atp ON atp.allowance_type_id = ea.allowance_type_id
            WHERE ea.effective_from <= ?
              AND (ea.effective_to IS NULL OR ea.effective_to >= ?)
            ORDER BY ea.employee_id, atp.allowance_type_id
        """;

        jdbc.query(allowanceSql, rs -> {
            long empId = rs.getLong("employee_id");
            EmployeeSalaryProfileDTO p = profileByEmp.get(empId);
            if (p == null) {
                return;
            }
            BigDecimal cfgAmt = rs.getBigDecimal("amount");
            String amountType = rs.getString("amount_type");
            BigDecimal resolvedAmt = "PERCENT_BASE_SALARY".equalsIgnoreCase(amountType) && p.getBaseSalary() != null
                    ? p.getBaseSalary().multiply(cfgAmt).divide(BigDecimal.valueOf(100), 0, RoundingMode.HALF_UP)
                    : cfgAmt;

            EmployeeSalaryProfileDTO.AllowanceItemDTO item = new EmployeeSalaryProfileDTO.AllowanceItemDTO(
                    rs.getString("type_code"),
                    rs.getString("type_name"),
                    resolvedAmt,
                    rs.getString("calculation_basis")
            );
            p.getAllowances().add(item);
        }, periodEnd, periodStart);

        return profiles;
    }

    // =========================================================================
    // 1. CHI TIẾT MINH CHỨNG NGÀY CÔNG (CHECK-IN, CHECK-OUT, SỐ GIỜ LÀM, LÝ DO)
    // =========================================================================
    public EmployeePayrollWorkDetailDTO getEmployeeWorkDetails(Long batchId, Long employeeId) {
        PayrollBatch batch = batchRepository.findById(batchId)
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "Không tìm thấy đợt lương #" + batchId));
        PayrollPeriod period = periodRepository.findById(batch.getPeriodId())
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "Không tìm thấy kỳ lương"));

        AttendanceSummaryDTO summary = new AttendanceSummaryDTO(
                employeeId,
                BigDecimal.valueOf(PayrollConstants.DEFAULT_STANDARD_WORK_DAYS),
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO
        );

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

        List<Map<String, Object>> empRows = jdbc.queryForList("""
            SELECT TOP 1 e.employee_code, e.full_name,
                   COALESCE(d.department_name, N'Chưa phân công') AS department_name
            FROM dbo.employees e
            OUTER APPLY (
                SELECT TOP 1 a.department_id
                FROM dbo.employee_assignments a
                WHERE a.employee_id = e.employee_id
                  AND a.effective_from <= ? AND (a.effective_to IS NULL OR a.effective_to > ?)
                ORDER BY a.effective_from DESC, a.assignment_id DESC
            ) a
            LEFT JOIN dbo.departments d ON d.department_id = a.department_id
            WHERE e.employee_id = ?
        """, periodEnd, periodStart, employeeId);

        Map<String, Object> empInfo = empRows.isEmpty()
                ? Map.of("employee_code", "NV-" + employeeId, "full_name", "Nhân viên #" + employeeId, "department_name", "Chưa phân công")
                : empRows.get(0);

        record PaidLeaveWindow(LocalDate start, LocalDate end, String requestCode, String leaveTypeName, String reason) {}
        List<PaidLeaveWindow> paidLeaveRanges = new ArrayList<>();
        jdbc.query("""
            SELECT CAST(ld.leave_start AS date) AS l_start,
                   CAST(ld.leave_end AS date) AS l_end,
                   r.request_code,
                   lt.type_name AS leave_type_name,
                   r.reason
            FROM dbo.employee_requests r
            JOIN dbo.leave_request_details ld ON ld.request_id = r.request_id
            JOIN dbo.leave_types lt ON lt.leave_type_id = ld.leave_type_id
            WHERE r.employee_id = ?
              AND r.status = 'DA_DUYET'
              AND lt.is_paid = 1
              AND ld.leave_start < ? AND ld.leave_end >= ?
        """, rs -> {
            paidLeaveRanges.add(new PaidLeaveWindow(
                    rs.getDate("l_start").toLocalDate(),
                    rs.getDate("l_end").toLocalDate(),
                    rs.getString("request_code"),
                    rs.getString("leave_type_name"),
                    rs.getString("reason")
            ));
        }, employeeId, periodEnd.plusDays(1).atStartOfDay(), periodStart.atStartOfDay());

        BigDecimal approvedOtHours = jdbc.queryForObject("""
            SELECT COALESCE(SUM(
                CAST(DATEDIFF(minute,
                    COALESCE(o.actual_start, o.requested_start),
                    COALESCE(o.actual_end, o.requested_end)
                ) AS decimal(10,2)) / 60.0
            ), 0)
            FROM dbo.overtime_requests o
            WHERE o.employee_id = ?
              AND o.status = 'DA_DUYET'
              AND o.confirmed_at IS NOT NULL
              AND o.requested_start >= ? AND o.requested_start < ?
        """, BigDecimal.class, employeeId, periodStart.atStartOfDay(), periodEnd.plusDays(1).atStartOfDay());
        if (approvedOtHours == null) {
            approvedOtHours = BigDecimal.ZERO;
        } else {
            approvedOtHours = approvedOtHours.max(BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP);
        }

        List<DailyWorkEvidenceDTO> dailyRecords = new ArrayList<>();
        final long[] totalLateMinsHolder = {0L};
        final int[] forfeitedLunchHolder = {0};
        final BigDecimal[] totalWorkedHrsHolder = {BigDecimal.ZERO};
        final BigDecimal[] actualWorkDaysHolder = {BigDecimal.ZERO};
        final BigDecimal[] paidLeaveDaysHolder = {BigDecimal.ZERO};
        final BigDecimal[] unexcusedDaysHolder = {BigDecimal.ZERO};

        String sql = """
            SELECT ws.schedule_id, ws.work_date, ws.planned_start, ws.planned_end, ws.planned_break_minutes,
                   ws.note AS schedule_note,
                   COALESCE(sh.shift_name, N'Ca hành chính tiêu chuẩn') AS shift_name,
                   ar.attendance_id,
                   COALESCE(ar.check_in_reviewed_value, ar.actual_start) AS eff_start,
                   COALESCE(ar.check_out_reviewed_value, ar.actual_end) AS eff_end,
                   COALESCE(ar.actual_break_minutes, ws.planned_break_minutes) AS break_mins,
                   ar.attendance_result,
                   COALESCE(ar.verification_status, 'CHUA_XAC_NHAN') AS verification_status,
                   ar.correction_reason
            FROM dbo.work_schedules ws
            LEFT JOIN dbo.work_shifts sh ON sh.shift_id = ws.shift_id
            LEFT JOIN dbo.attendance_records ar ON ar.schedule_id = ws.schedule_id AND ar.invalidated_at IS NULL
            WHERE ws.employee_id = ?
              AND ws.cancelled_at IS NULL
              AND ws.work_date BETWEEN ? AND ?
            ORDER BY ws.work_date ASC, ws.planned_start ASC
        """;

        LocalDate today = LocalDate.now();

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
            String correctionReason = cleanSeedPrefix(rs.getString("correction_reason"));
            String scheduleNote = cleanSeedPrefix(rs.getString("schedule_note"));

            long plannedNetMins = Math.max(0, ChronoUnit.MINUTES.between(plannedStart, plannedEnd) - plannedBreak);
            BigDecimal plannedHours = BigDecimal.valueOf(plannedNetMins)
                    .divide(BigDecimal.valueOf(60), 2, RoundingMode.HALF_UP);

            Optional<PaidLeaveWindow> matchedLeave = paidLeaveRanges.stream()
                    .filter(rng -> !workDate.isBefore(rng.start()) && !workDate.isAfter(rng.end()))
                    .findFirst();

            BigDecimal workedHours = BigDecimal.ZERO;
            long lateMinutes = 0;
            long earlyLeaveMinutes = 0;
            boolean lunchForfeited = false;
            String displayResult;
            StringBuilder note = new StringBuilder();

            if ("DI_LAM".equals(rawResult) && actualStart != null) {
                displayResult = "DI_LAM";
                actualWorkDaysHolder[0] = actualWorkDaysHolder[0].add(BigDecimal.ONE);

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

                // Quy tắc: Trễ quá 1/2 ngày làm việc (> 240 phút với ca 8h) -> cắt phụ cấp cơm trưa ngày đó
                if (plannedNetMins > 0 && lateMinutes > (plannedNetMins / 2)) {
                    lunchForfeited = true;
                    forfeitedLunchHolder[0]++;
                }

                totalLateMinsHolder[0] += (lateMinutes + earlyLeaveMinutes);
                totalWorkedHrsHolder[0] = totalWorkedHrsHolder[0].add(workedHours);

                String checkInStr = actualStart.format(TIME_FMT);
                String checkOutStr = actualEnd != null ? actualEnd.format(TIME_FMT) : "Chưa check-out";

                if (lunchForfeited) {
                    note.append(String.format(
                            "Minh chứng vào %s - ra %s (%s giờ). Đi trễ %d phút (> 1/2 ca %d phút) → Phạt trừ %d phút lương & cắt phụ cấp cơm trưa ngày này",
                            checkInStr, checkOutStr, workedHours.stripTrailingZeros().toPlainString(),
                            lateMinutes, plannedNetMins / 2, lateMinutes + earlyLeaveMinutes));
                } else if (lateMinutes > 0 || earlyLeaveMinutes > 0) {
                    List<String> vParts = new ArrayList<>();
                    if (lateMinutes > 0) vParts.add("đi trễ " + lateMinutes + " phút");
                    if (earlyLeaveMinutes > 0) vParts.add("về sớm " + earlyLeaveMinutes + " phút");
                    note.append(String.format(
                            "Minh chứng vào %s - ra %s (%s giờ). Vi phạm: %s → Phạt trừ đúng %d phút lương",
                            checkInStr, checkOutStr, workedHours.stripTrailingZeros().toPlainString(),
                            String.join(", ", vParts), lateMinutes + earlyLeaveMinutes));
                } else if (actualEnd == null) {
                    note.append(String.format("Đã check-in lúc %s nhưng thiếu dữ liệu check-out", checkInStr));
                } else {
                    note.append(String.format("Đúng giờ (Vào %s - Ra %s, làm thực tế %s giờ, nghỉ giữa ca %d phút)",
                            checkInStr, checkOutStr, workedHours.stripTrailingZeros().toPlainString(), breakMins));
                }

                if (correctionReason != null && !correctionReason.isBlank()) {
                    note.append(" | Lý do/Ghi chú: ").append(correctionReason);
                } else if (scheduleNote != null && !scheduleNote.isBlank()) {
                    note.append(" | Ghi chú ca: ").append(scheduleNote);
                }
            } else if (matchedLeave.isPresent() || "NGHI_PHEP".equals(rawResult)) {
                displayResult = "NGHI_PHEP_CO_LUONG";
                workedHours = plannedHours;
                paidLeaveDaysHolder[0] = paidLeaveDaysHolder[0].add(BigDecimal.ONE);
                if (matchedLeave.isPresent()) {
                    PaidLeaveWindow lw = matchedLeave.get();
                    note.append(String.format("Nghỉ phép hưởng lương theo đơn [%s] (%s)",
                            lw.requestCode(), lw.leaveTypeName()));
                    if (lw.reason() != null && !lw.reason().isBlank()) {
                        note.append(" — Lý do: ").append(lw.reason());
                    }
                } else {
                    note.append("Nghỉ phép có hưởng lương đã được phê duyệt");
                }
            } else if ("VANG".equals(rawResult) || "VANG_KHONG_PHEP".equals(rawResult)) {
                displayResult = "VANG_KHONG_PHEP";
                unexcusedDaysHolder[0] = unexcusedDaysHolder[0].add(BigDecimal.ONE);
                note.append("Vắng mặt không phép (Không có log check-in/out và không có đơn nghỉ phép được duyệt) → Trừ 1 ngày lương");
                if (correctionReason != null && !correctionReason.isBlank()) {
                    note.append(" | Ghi chú: ").append(correctionReason);
                }
            } else {
                displayResult = "CHUA_CHAM_CONG";
                note.append("Ca làm việc chưa phát sinh dữ liệu chấm công");
            }

            dailyRecords.add(new DailyWorkEvidenceDTO(
                    scheduleId, attendanceId, workDate, shiftName,
                    plannedStart, plannedEnd, actualStart, actualEnd,
                    breakMins, plannedHours, workedHours,
                    lateMinutes, earlyLeaveMinutes, lunchForfeited,
                    displayResult, verStatus, note.toString()
            ));
        }, employeeId, periodStart, periodEnd);

        BigDecimal standardDays = !dailyRecords.isEmpty()
                ? BigDecimal.valueOf(dailyRecords.size())
                : (summary.getStandardWorkDays() != null && summary.getStandardWorkDays().compareTo(BigDecimal.ZERO) > 0
                    ? summary.getStandardWorkDays()
                    : BigDecimal.valueOf(PayrollConstants.DEFAULT_STANDARD_WORK_DAYS));

        BigDecimal resolvedActualDays = !dailyRecords.isEmpty()
                ? actualWorkDaysHolder[0]
                : (summary.getActualWorkDays() != null ? summary.getActualWorkDays() : standardDays);
        BigDecimal resolvedPaidLeave = !dailyRecords.isEmpty()
                ? paidLeaveDaysHolder[0]
                : (summary.getPaidLeaveDays() != null ? summary.getPaidLeaveDays() : BigDecimal.ZERO);
        BigDecimal resolvedOtHours = approvedOtHours.compareTo(BigDecimal.ZERO) > 0
                ? approvedOtHours
                : (summary.getOvertimeHours() != null ? summary.getOvertimeHours() : BigDecimal.ZERO);

        return new EmployeePayrollWorkDetailDTO(
                batchId,
                employeeId,
                String.valueOf(empInfo.get("employee_code")),
                String.valueOf(empInfo.get("full_name")),
                String.valueOf(empInfo.get("department_name")),
                standardDays,
                resolvedActualDays,
                totalWorkedHrsHolder[0],
                totalLateMinsHolder[0],
                BigDecimal.valueOf(totalLateMinsHolder[0]).divide(BigDecimal.valueOf(60), 2, RoundingMode.HALF_UP),
                forfeitedLunchHolder[0],
                unexcusedDaysHolder[0],
                resolvedPaidLeave,
                resolvedOtHours,
                dailyRecords
        );
    }

    private String cleanSeedPrefix(String raw) {
        if (raw == null) return null;
        String cleaned = raw.replaceAll("^SEED_[A-Za-z0-9_]+\\s*[:-]?\\s*", "").trim();
        if ("rollback-only payroll guard".equalsIgnoreCase(cleaned)
                || "Đi làm bình thường".equalsIgnoreCase(cleaned)
                || cleaned.startsWith("SEED_")) {
            return null;
        }
        return cleaned.isEmpty() ? null : cleaned;
    }

    // =========================================================================
    // 2. XEM DANH SÁCH PHỤ CẤP CỦA THÁNG & CỦA PHÒNG BAN
    // =========================================================================
    public PeriodAllowanceOverviewDTO getPeriodAllowances(Long periodId, Long departmentId) {
        PayrollPeriod period = periodRepository.findById(periodId)
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "Không tìm thấy kỳ lương #" + periodId));

        String sql = """
            SELECT ea.employee_allowance_id, ea.employee_id, e.employee_code, e.full_name,
                   COALESCE(d.department_id, 0) AS department_id,
                   COALESCE(d.department_code, 'CHUA_PC') AS department_code,
                   COALESCE(d.department_name, N'Chưa phân công') AS department_name,
                   atp.type_code, atp.type_name, ea.calculation_basis, ea.amount_type, ea.amount,
                   ea.effective_from, ea.effective_to, atp.is_taxable, atp.counts_for_social_insurance,
                   ea.condition_note,
                   COALESCE((
                       SELECT TOP 1 st.base_salary
                       FROM dbo.contracts c
                       JOIN dbo.contract_salary_terms st ON st.contract_id = c.contract_id
                       WHERE c.employee_id = e.employee_id
                         AND c.status IN ('DA_KY', 'DA_KET_THUC', 'HET_HAN')
                         AND st.effective_from <= ? AND (st.effective_to IS NULL OR st.effective_to >= ?)
                       ORDER BY st.effective_from DESC, st.salary_term_id DESC
                   ), 0) AS base_salary
            FROM dbo.employee_allowances ea
            JOIN dbo.allowance_types atp ON atp.allowance_type_id = ea.allowance_type_id
            JOIN dbo.employees e ON e.employee_id = ea.employee_id
            OUTER APPLY (
                SELECT TOP 1 a.department_id
                FROM dbo.employee_assignments a
                WHERE a.employee_id = e.employee_id
                  AND a.effective_from <= ? AND (a.effective_to IS NULL OR a.effective_to > ?)
                ORDER BY a.effective_from DESC, a.assignment_id DESC
            ) a
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
            Long deptIdObj = rs.getLong("department_id");
            String deptCode = rs.getString("department_code");
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
                    cleanSeedPrefix(rs.getString("condition_note"))
            );

            DepartmentAccumulator acc = deptMap.computeIfAbsent(deptIdObj,
                    k -> new DepartmentAccumulator(deptIdObj, deptCode, deptName));
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
                   COALESCE(pos.position_name, N'Chuyên viên / Nhân viên') AS position_name,
                   COALESCE((SELECT SUM(pl.amount) FROM dbo.payroll_lines pl
                             WHERE pl.payroll_detail_id = pd.payroll_detail_id AND pl.direction_snapshot = 'ADD'), 0) AS total_gross,
                   COALESCE((SELECT SUM(pl.amount) FROM dbo.payroll_lines pl
                             WHERE pl.payroll_detail_id = pd.payroll_detail_id AND pl.direction_snapshot = 'DEDUCT'), 0) AS total_deduct,
                   COALESCE(
                       TRY_CONVERT(decimal(10,2), JSON_VALUE(snap.snapshot_json, '$.actualWorkDays')),
                       ROUND(TRY_CONVERT(decimal(10,2), JSON_VALUE(snap.snapshot_json, '$.paidMinutes')) / 480.0, 2),
                       (SELECT TOP 1 pl.quantity FROM dbo.payroll_lines pl
                        WHERE pl.payroll_detail_id = pd.payroll_detail_id AND pl.component_code_snapshot = 'LUONG_CONG' AND pl.quantity IS NOT NULL),
                       26
                   ) AS actual_work_days,
                   COALESCE(
                       TRY_CONVERT(decimal(10,2), JSON_VALUE(snap.snapshot_json, '$.overtimeHours')),
                       ROUND(TRY_CONVERT(decimal(10,2), JSON_VALUE(snap.snapshot_json, '$.overtimeMinutes')) / 60.0, 2),
                       (SELECT TOP 1 pl.quantity FROM dbo.payroll_lines pl
                        WHERE pl.payroll_detail_id = pd.payroll_detail_id AND pl.component_code_snapshot = 'TANG_CA' AND pl.quantity IS NOT NULL),
                       0
                   ) AS overtime_hours,
                   ps.payslip_id
            FROM dbo.payroll_details pd
            JOIN dbo.payroll_batches pb ON pb.batch_id = pd.batch_id
            JOIN dbo.payroll_periods pp ON pp.period_id = pb.period_id
            JOIN dbo.employees e ON e.employee_id = pd.employee_id
            OUTER APPLY (
                SELECT TOP 1 a.department_id, a.position_id
                FROM dbo.employee_assignments a
                WHERE a.employee_id = e.employee_id
                  AND a.effective_from <= pp.period_end AND (a.effective_to IS NULL OR a.effective_to > pp.period_start)
                ORDER BY a.effective_from DESC, a.assignment_id DESC
            ) a
            LEFT JOIN dbo.departments d ON d.department_id = a.department_id
            LEFT JOIN dbo.positions pos ON pos.position_id = a.position_id
            LEFT JOIN dbo.payslips ps ON ps.payroll_detail_id = pd.payroll_detail_id
            OUTER APPLY (
                SELECT TOP 1 CASE WHEN ISJSON(s.input_snapshot_json) = 1 THEN s.input_snapshot_json END AS snapshot_json
                FROM dbo.payroll_snapshots s
                WHERE s.payroll_detail_id = pd.payroll_detail_id
                ORDER BY s.captured_at DESC
            ) snap
            WHERE EXISTS (SELECT 1 FROM dbo.payroll_lines pl WHERE pl.payroll_detail_id = pd.payroll_detail_id)
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
            } else if ("DA_CHOT".equalsIgnoreCase(status)) {
                sql.append(" AND pb.status = 'DA_CHOT' AND pb.published_at IS NULL");
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
                    rs.getBigDecimal("actual_work_days"),
                    rs.getBigDecimal("overtime_hours"),
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

        // Ưu tiên chọn đợt lương mới nhất đã có dữ liệu tính toán (payroll_lines)
        Long latestBatchId = jdbc.query("""
                SELECT TOP 1 pb.batch_id
                FROM dbo.payroll_batches pb
                WHERE pb.period_id = ?
                ORDER BY
                    CASE WHEN EXISTS (
                        SELECT 1 FROM dbo.payroll_details pd
                        JOIN dbo.payroll_lines pl ON pl.payroll_detail_id = pd.payroll_detail_id
                        WHERE pd.batch_id = pb.batch_id
                    ) THEN 0 ELSE 1 END,
                    pb.version_no DESC
                """,
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
            OUTER APPLY (
                SELECT TOP 1 a.department_id
                FROM dbo.employee_assignments a
                WHERE a.employee_id = pd.employee_id
                  AND a.effective_from <= ? AND (a.effective_to IS NULL OR a.effective_to > ?)
                ORDER BY a.effective_from DESC, a.assignment_id DESC
            ) a
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

    private record DecisionSummary(BigDecimal totalAmount, String explanation) {}

    private DecisionSummary queryApprovedDecisionSummary(Long employeeId, LocalDate start, LocalDate end, String effect) {
        if (jdbc == null) {
            return new DecisionSummary(BigDecimal.ZERO, null);
        }
        List<String> reasons = new ArrayList<>();
        final BigDecimal[] sumHolder = {BigDecimal.ZERO};

        jdbc.query("""
            SELECT decision_number, decision_type, amount, apply_date, content
            FROM dbo.employee_decisions
            WHERE employee_id = ?
              AND approval_status = 'DA_DUYET'
              AND payroll_effect = ?
              AND apply_date BETWEEN ? AND ?
            ORDER BY apply_date ASC, decision_id ASC
        """, rs -> {
            BigDecimal amt = rs.getBigDecimal("amount");
            if (amt != null && amt.compareTo(BigDecimal.ZERO) > 0) {
                sumHolder[0] = sumHolder[0].add(amt);
                String num = rs.getString("decision_number");
                String dt = rs.getDate("apply_date") != null ? rs.getDate("apply_date").toLocalDate().toString() : "";
                String content = rs.getString("content");
                String reasonText = (content != null && !content.isBlank()) ? content.trim() : "Quyết định nhân sự";
                reasons.add(String.format("QĐ [%s] ngày %s: %s (%s đ)",
                        num, dt, reasonText, amt.setScale(0, RoundingMode.HALF_UP).toPlainString()));
            }
        }, employeeId, effect, start, end);

        String joined = reasons.isEmpty() ? null : String.join("; ", reasons);
        return new DecisionSummary(sumHolder[0], joined);
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
