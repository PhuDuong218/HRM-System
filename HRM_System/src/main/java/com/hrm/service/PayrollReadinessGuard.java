package com.hrm.service;

import com.hrm.dto.PayrollEnhancementDTOs.PayrollBlockerItemDTO;
import com.hrm.dto.PayrollEnhancementDTOs.PayrollReadinessReportDTO;
import com.hrm.exception.BusinessException;
import com.hrm.model.PayrollBatch;
import com.hrm.model.PayrollPeriod;
import com.hrm.repository.PayrollBatchRepository;
import com.hrm.repository.PayrollPeriodRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class PayrollReadinessGuard {

    private final JdbcTemplate jdbc;
    private final Clock clock;
    private final PayrollBatchRepository batchRepository;
    private final PayrollPeriodRepository periodRepository;

    // Mở khóa cửa sổ ngày 03-05 đầu tháng kế tiếp theo mặc định để phục vụ Demo & kiểm thử mọi thời điểm
    @Value("${hrm.payroll.enforce-calculate-window:false}")
    private boolean enforceCalculateWindow = false;

    @Value("${hrm.payroll.enforce-lock-window:false}")
    private boolean enforceLockWindow = false;

    @Autowired
    public PayrollReadinessGuard(
            JdbcTemplate jdbc,
            @Qualifier("hrmBusinessClock") Clock clock,
            PayrollBatchRepository batchRepository,
            PayrollPeriodRepository periodRepository) {
        this.jdbc = jdbc;
        this.clock = clock;
        this.batchRepository = batchRepository;
        this.periodRepository = periodRepository;
    }

    public PayrollReadinessGuard(JdbcTemplate jdbc, Clock clock) {
        this(jdbc, clock, null, null);
    }

    public PayrollReadinessGuard(JdbcTemplate jdbc) {
        this(jdbc, Clock.system(ZoneId.of("Asia/Ho_Chi_Minh")), null, null);
    }

    /**
     * Giữ tương thích với PayrollReadinessWiringTest và các bài test tích hợp hiện có.
     */
    public static boolean withinClosingWindow(LocalDate periodEnd, LocalDate today) {
        if (periodEnd == null || today == null) {
            return false;
        }
        LocalDate nextMonth = periodEnd.plusMonths(1);
        LocalDate windowStart = nextMonth.withDayOfMonth(3);
        LocalDate windowEnd = nextMonth.withDayOfMonth(5);
        return !today.isBefore(windowStart) && !today.isAfter(windowEnd);
    }

    /**
     * Kiểm tra điều kiện sẵn sàng theo periodId (hoặc batchId).
     * Nếu khởi tạo qua constructor 2 tham số (PayrollReadinessGuardIntegrationTest), chạy kiểm tra trực tiếp trên JdbcTemplate.
     */
    public void requireReady(long periodOrBatchId) {
        if (batchRepository == null || periodRepository == null) {
            requireReadyDirectJdbc(periodOrBatchId);
            return;
        }

        PayrollPeriod period = periodRepository.findById(periodOrBatchId).orElse(null);
        PayrollBatch batch = null;
        if (period == null) {
            batch = batchRepository.findById(periodOrBatchId)
                    .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND,
                            "Không tìm thấy kỳ lương hoặc đợt bảng lương ID: " + periodOrBatchId));
            Long pid = batch.getPeriodId();
            period = periodRepository.findById(pid)
                    .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "Không tìm thấy kỳ lương ID: " + pid));
        }
        assertReadyForLock(batch, period);
    }

    private void requireReadyDirectJdbc(long periodId) {
        if (!TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException("Payroll readiness requires a transaction");
        }
        var periods = jdbc.query(
                "SELECT period_start, period_end FROM dbo.payroll_periods WITH(UPDLOCK,HOLDLOCK) WHERE period_id=?",
                (rs, n) -> new LocalDate[]{rs.getDate(1).toLocalDate(), rs.getDate(2).toLocalDate()},
                periodId
        );
        if (periods.size() != 1) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy kỳ lương.");
        }
        LocalDate start = periods.get(0)[0], end = periods.get(0)[1];
        var errors = new ArrayList<String>();
        if (!withinClosingWindow(end, LocalDate.now(clock))) {
            errors.add("chỉ được tính/chốt lương từ ngày 3 đến hết ngày 5 tháng kế tiếp ("
                    + end.plusMonths(1).withDayOfMonth(3) + " đến " + end.plusMonths(1).withDayOfMonth(5) + ")");
        }
        LocalDateTime from = start.atStartOfDay(), until = end.plusDays(1).atStartOfDay();
        countDirect(errors, "lịch/công chưa xác nhận", """
            SELECT COUNT_BIG(*) FROM dbo.work_schedules s WITH(HOLDLOCK)
            LEFT JOIN dbo.attendance_records a WITH(HOLDLOCK) ON a.schedule_id=s.schedule_id AND a.invalidated_at IS NULL
            WHERE s.cancelled_at IS NULL AND (s.work_date BETWEEN ? AND ? OR s.planned_start<? AND s.planned_end>? OR a.actual_start<? AND a.actual_end>?)
              AND (a.attendance_id IS NULL OR a.verification_status<>'DA_XAC_NHAN'
                OR a.attendance_result='DI_LAM' AND (a.actual_start IS NULL OR a.actual_end IS NULL))
            """, start, end, until, from, until, from);
        countDirect(errors, "yêu cầu bổ sung/điều chỉnh công đang mở", """
            SELECT COUNT_BIG(*) FROM dbo.attendance_portal_requests r WITH(HOLDLOCK)
            JOIN dbo.work_schedules s WITH(HOLDLOCK) ON s.schedule_id=r.schedule_id
            WHERE r.status IN ('CHO_XU_LY','DANG_XU_LY') AND (s.work_date BETWEEN ? AND ? OR s.planned_start<? AND s.planned_end>? OR r.proposed_start<? AND r.proposed_end>?)
            """, start, end, until, from, until, from);
        countDirect(errors, "đơn OT chưa duyệt hoặc chưa xác nhận giờ thực tế", """
            SELECT COUNT_BIG(*) FROM dbo.overtime_requests o WITH(HOLDLOCK)
            WHERE (o.requested_start<? AND o.requested_end>? OR o.actual_start<? AND o.actual_end>?)
              AND (o.status='CHO_DUYET' OR o.status='DA_DUYET' AND
                (o.confirmed_at IS NULL OR o.confirmed_by IS NULL OR o.actual_start IS NULL OR o.actual_end IS NULL))
            """, until, from, until, from);
        if (!errors.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Chưa được tính/chốt lương: " + String.join("; ", errors) + ". Hoàn tất xử lý và xác nhận trước.");
        }
    }

    private void countDirect(List<String> errors, String label, String sql, Object... args) {
        Long n = jdbc.queryForObject(sql, Long.class, args);
        if (n == null) {
            throw new IllegalStateException("Cannot verify payroll inputs");
        }
        if (n > 0) {
            errors.add(n + " " + label);
        }
    }

    public void assertReadyForCalculate(PayrollBatch batch, PayrollPeriod period) {
        PayrollReadinessReportDTO report = inspect(batch, period);
        if (!report.readyForCalculate()) {
            throw new BusinessException(HttpStatus.CONFLICT, report.summaryMessage());
        }
    }

    public void assertReadyForLock(PayrollBatch batch, PayrollPeriod period) {
        PayrollReadinessReportDTO report = inspect(batch, period);
        if (!report.readyForLock()) {
            throw new BusinessException(HttpStatus.CONFLICT, report.summaryMessage());
        }
    }

    public PayrollReadinessReportDTO inspect(PayrollBatch batch, PayrollPeriod period) {
        List<PayrollBlockerItemDTO> blockers = new ArrayList<>();
        LocalDate start = period.getPeriodStart();
        LocalDate end = period.getPeriodEnd();
        LocalDateTime startDt = start.atStartOfDay();
        LocalDateTime endExclusiveDt = end.plusDays(1).atStartOfDay();

        // 1. KIỂM TRA CỬA SỔ THỜI GIAN TÍNH / CHỐT LƯƠNG (NGÀY 3 ĐẾN HẾT NGÀY 5 THÁNG KẾ TIẾP)
        LocalDate today = LocalDate.now(clock);
        LocalDate nextMonth = end.plusMonths(1);
        LocalDate windowStart = nextMonth.withDayOfMonth(3);
        LocalDate windowEnd = nextMonth.withDayOfMonth(5);
        boolean inWindow = !today.isBefore(windowStart) && !today.isAfter(windowEnd);

        if (!inWindow && (enforceCalculateWindow || enforceLockWindow)) {
            blockers.add(new PayrollBlockerItemDTO(
                    "CUA_SO_THOI_GIAN",
                    "Ngoài thời gian chốt sổ quy định",
                    null, "HỆ THỐNG", "Toàn bộ kỳ lương " + period.getPeriodCode(), "Tất cả phòng ban",
                    period.getPeriodCode(),
                    windowStart + " đến " + windowEnd,
                    String.format("Quy định chỉ cho phép chốt bảng lương từ ngày 03 đến hết ngày 05 tháng kế tiếp (%s đến %s). Ngày hệ thống hiện tại: %s.",
                            windowStart, windowEnd, today),
                    "/payroll"
            ));
        }

        // 2. ĐƠN NGHỈ PHÉP / NGHỈ VIỆC CHƯA DUYỆT
        String pendingReqSql = """
            SELECT r.request_code, r.request_type, r.reason, e.employee_id, e.employee_code, e.full_name,
                   COALESCE(d.department_name, N'Chưa phân công') AS dept_name,
                   CAST(COALESCE(ld.leave_start, r.submitted_at) AS date) AS ref_date
            FROM dbo.employee_requests r
            JOIN dbo.employees e ON e.employee_id = r.employee_id
            LEFT JOIN dbo.leave_request_details ld ON ld.request_id = r.request_id
            OUTER APPLY (
                SELECT TOP 1 a.department_id
                FROM dbo.employee_assignments a
                WHERE a.employee_id = e.employee_id
                  AND a.effective_from <= ? AND (a.effective_to IS NULL OR a.effective_to > ?)
                ORDER BY a.effective_from DESC, a.assignment_id DESC
            ) a
            LEFT JOIN dbo.departments d ON d.department_id = a.department_id
            WHERE r.status = 'CHO_DUYET'
              AND (
                  (ld.request_id IS NOT NULL AND ld.leave_start < ? AND ld.leave_end >= ?)
                  OR (ld.request_id IS NULL AND r.submitted_at >= ? AND r.submitted_at < ?)
              )
            ORDER BY e.employee_code, r.request_code
        """;
        jdbc.query(pendingReqSql, rs -> {
            String empCode = rs.getString("employee_code");
            String fullName = rs.getString("full_name");
            String deptName = rs.getString("dept_name");
            String reqCode = rs.getString("request_code");
            String reqType = "NGHI_PHEP".equals(rs.getString("request_type")) ? "Đơn nghỉ phép" : "Đơn nghỉ việc";
            String refDate = String.valueOf(rs.getDate("ref_date"));
            String reqReason = rs.getString("reason");
            String reasonSuffix = (reqReason != null && !reqReason.isBlank()) ? " (Lý do đơn: " + reqReason + ")" : "";
            blockers.add(new PayrollBlockerItemDTO(
                    "DON_NGHI_PHEP",
                    reqType + " chưa duyệt",
                    rs.getLong("employee_id"), empCode, fullName, deptName,
                    reqCode, refDate,
                    String.format("Nhân viên %s (Mã NV: %s — %s) có %s mã [%s] ngày %s đang ở trạng thái CHỜ DUYỆT%s.",
                            fullName, empCode, deptName, reqType, reqCode, refDate, reasonSuffix),
                    "/requests"
            ));
        }, end, start, endExclusiveDt, startDt, startDt, endExclusiveDt);

        // 3. ĐƠN TĂNG CA (OT) CHƯA DUYỆT HOẶC CHƯA XÁC NHẬN GIỜ THỰC TẾ
        String pendingOtSql = """
            SELECT o.request_code, o.status, o.confirmed_at, o.reason, e.employee_id, e.employee_code, e.full_name,
                   COALESCE(d.department_name, N'Chưa phân công') AS dept_name,
                   CAST(o.requested_start AS date) AS work_date
            FROM dbo.overtime_requests o
            JOIN dbo.employees e ON e.employee_id = o.employee_id
            OUTER APPLY (
                SELECT TOP 1 a.department_id
                FROM dbo.employee_assignments a
                WHERE a.employee_id = e.employee_id
                  AND a.effective_from <= ? AND (a.effective_to IS NULL OR a.effective_to > ?)
                ORDER BY a.effective_from DESC, a.assignment_id DESC
            ) a
            LEFT JOIN dbo.departments d ON d.department_id = a.department_id
            WHERE o.requested_start < ? AND o.requested_end >= ?
              AND (o.status = 'CHO_DUYET' OR (o.status = 'DA_DUYET' AND o.confirmed_at IS NULL))
            ORDER BY e.employee_code, o.request_code
        """;
        jdbc.query(pendingOtSql, rs -> {
            String empCode = rs.getString("employee_code");
            String fullName = rs.getString("full_name");
            String deptName = rs.getString("dept_name");
            String reqCode = rs.getString("request_code");
            String status = rs.getString("status");
            String workDate = String.valueOf(rs.getDate("work_date"));
            String reason = "CHO_DUYET".equals(status)
                    ? "đang CHỜ DUYỆT tăng ca"
                    : "đã duyệt nhưng CHƯA XÁC NHẬN giờ tăng ca thực tế";
            blockers.add(new PayrollBlockerItemDTO(
                    "DON_TANG_CA",
                    "Đơn tăng ca (OT) chưa hoàn tất",
                    rs.getLong("employee_id"), empCode, fullName, deptName,
                    reqCode, workDate,
                    String.format("Nhân viên %s (Mã NV: %s — %s) có đơn OT [%s] ngày %s %s.",
                            fullName, empCode, deptName, reqCode, workDate, reason),
                    "/overtime"
            ));
        }, end, start, endExclusiveDt, startDt);

        // 4. ĐƠN GIẢI TRÌNH CHẤM CÔNG CHƯA XỬ LÝ
        String pendingAttReqSql = """
            SELECT pr.request_code, pr.status, e.employee_id, e.employee_code, e.full_name,
                   COALESCE(d.department_name, N'Chưa phân công') AS dept_name,
                   ws.work_date
            FROM dbo.attendance_portal_requests pr
            JOIN dbo.work_schedules ws ON ws.schedule_id = pr.schedule_id
            JOIN dbo.employees e ON e.employee_id = ws.employee_id
            OUTER APPLY (
                SELECT TOP 1 a.department_id
                FROM dbo.employee_assignments a
                WHERE a.employee_id = e.employee_id
                  AND a.effective_from <= ? AND (a.effective_to IS NULL OR a.effective_to > ?)
                ORDER BY a.effective_from DESC, a.assignment_id DESC
            ) a
            LEFT JOIN dbo.departments d ON d.department_id = a.department_id
            WHERE ws.work_date BETWEEN ? AND ?
              AND pr.status IN ('CHO_XU_LY', 'DANG_XU_LY')
            ORDER BY e.employee_code, ws.work_date
        """;
        jdbc.query(pendingAttReqSql, rs -> {
            String empCode = rs.getString("employee_code");
            String fullName = rs.getString("full_name");
            String deptName = rs.getString("dept_name");
            String reqCode = rs.getString("request_code");
            String workDate = String.valueOf(rs.getDate("work_date"));
            blockers.add(new PayrollBlockerItemDTO(
                    "DON_CHAM_CONG",
                    "Đơn giải trình chấm công chờ duyệt",
                    rs.getLong("employee_id"), empCode, fullName, deptName,
                    reqCode, workDate,
                    String.format("Nhân viên %s (Mã NV: %s — %s) có đơn giải trình công [%s] ngày %s chưa được xử lý.",
                            fullName, empCode, deptName, reqCode, workDate),
                    "/attendance"
            ));
        }, end, start, start, end);

        // 5. QUYẾT ĐỊNH KHEN THƯỞNG / KỶ LUẬT ẢNH HƯỞNG LƯƠNG CHỜ DUYỆT
        String pendingDecisionSql = """
            SELECT dec.decision_number, dec.decision_type, dec.apply_date,
                   e.employee_id, e.employee_code, e.full_name,
                   COALESCE(d.department_name, N'Chưa phân công') AS dept_name
            FROM dbo.employee_decisions dec
            JOIN dbo.employees e ON e.employee_id = dec.employee_id
            OUTER APPLY (
                SELECT TOP 1 a.department_id
                FROM dbo.employee_assignments a
                WHERE a.employee_id = e.employee_id
                  AND a.effective_from <= ? AND (a.effective_to IS NULL OR a.effective_to > ?)
                ORDER BY a.effective_from DESC, a.assignment_id DESC
            ) a
            LEFT JOIN dbo.departments d ON d.department_id = a.department_id
            WHERE dec.approval_status = 'CHO_DUYET'
              AND dec.payroll_effect <> 'NONE'
              AND dec.apply_date BETWEEN ? AND ?
            ORDER BY e.employee_code
        """;
        jdbc.query(pendingDecisionSql, rs -> {
            String empCode = rs.getString("employee_code");
            String fullName = rs.getString("full_name");
            String deptName = rs.getString("dept_name");
            String decNum = rs.getString("decision_number");
            String applyDate = String.valueOf(rs.getDate("apply_date"));
            blockers.add(new PayrollBlockerItemDTO(
                    "QUYET_DINH_LUONG",
                    "Quyết định thưởng/phạt chờ duyệt",
                    rs.getLong("employee_id"), empCode, fullName, deptName,
                    decNum, applyDate,
                    String.format("Nhân viên %s (Mã NV: %s — %s) có quyết định [%s] áp dụng ngày %s đang CHỜ DUYỆT.",
                            fullName, empCode, deptName, decNum, applyDate),
                    "/decisions"
            ));
        }, end, start, start, end);

        // 6. LỊCH LÀM VIỆC / BẢN CHẤM CÔNG CHƯA XÁC NHẬN (Chỉ kiểm tra đến ngày hiện tại nếu kỳ lương đang diễn ra)
        LocalDate attendanceCheckEnd = end.isAfter(today) ? today : end;
        if (!attendanceCheckEnd.isBefore(start)) {
            String unconfirmedAttSql = """
                SELECT e.employee_id, e.employee_code, e.full_name,
                       COALESCE(d.department_name, N'Chưa phân công') AS dept_name,
                       ws.work_date,
                       ar.verification_status
                FROM dbo.work_schedules ws
                JOIN dbo.employees e ON e.employee_id = ws.employee_id
                LEFT JOIN dbo.attendance_records ar ON ar.schedule_id = ws.schedule_id AND ar.invalidated_at IS NULL
                OUTER APPLY (
                    SELECT TOP 1 a.department_id
                    FROM dbo.employee_assignments a
                    WHERE a.employee_id = e.employee_id
                      AND a.effective_from <= ? AND (a.effective_to IS NULL OR a.effective_to > ?)
                    ORDER BY a.effective_from DESC, a.assignment_id DESC
                ) a
                LEFT JOIN dbo.departments d ON d.department_id = a.department_id
                WHERE ws.cancelled_at IS NULL
                  AND ws.work_date BETWEEN ? AND ?
                  AND e.employment_status = 'DANG_LAM'
                  AND (ar.attendance_id IS NULL OR ar.verification_status <> 'DA_XAC_NHAN')
                ORDER BY e.employee_code, ws.work_date
            """;
            Map<Long, List<String>> unconfirmedByEmp = new LinkedHashMap<>();
            Map<Long, String[]> empMeta = new LinkedHashMap<>();
            jdbc.query(unconfirmedAttSql, rs -> {
                long empId = rs.getLong("employee_id");
                empMeta.putIfAbsent(empId, new String[]{
                        rs.getString("employee_code"),
                        rs.getString("full_name"),
                        rs.getString("dept_name")
                });
                String dStr = String.valueOf(rs.getDate("work_date"));
                String vStatus = rs.getString("verification_status");
                unconfirmedByEmp.computeIfAbsent(empId, k -> new ArrayList<>())
                        .add(vStatus == null ? dStr + " (chưa chấm công)" : dStr + " (chưa xác nhận)");
            }, end, start, start, attendanceCheckEnd);

            for (var entry : unconfirmedByEmp.entrySet()) {
                long empId = entry.getKey();
                String[] meta = empMeta.get(empId);
                List<String> dates = entry.getValue();
                String sampleDates = dates.stream().limit(5).collect(Collectors.joining(", "))
                        + (dates.size() > 5 ? " ... (tổng " + dates.size() + " ngày)" : "");
                blockers.add(new PayrollBlockerItemDTO(
                        "CHAM_CONG_CHUA_XAC_NHAN",
                        "Chấm công chưa xác nhận (" + dates.size() + " ngày)",
                        empId, meta[0], meta[1], meta[2],
                        dates.size() + " ca làm",
                        sampleDates,
                        String.format("Nhân viên %s (Mã NV: %s — %s) còn %d ngày công chưa xác nhận: %s.",
                                meta[1], meta[0], meta[2], dates.size(), sampleDates),
                        "/attendance"
                ));
            }
        }

        // 7. NHÂN VIÊN THIẾU HỢP ĐỒNG / ĐIỀU KHOẢN LƯƠNG HỢP LỆ
        String missingSalarySql = """
            SELECT e.employee_id, e.employee_code, e.full_name,
                   COALESCE(d.department_name, N'Chưa phân công') AS dept_name
            FROM dbo.employees e
            OUTER APPLY (
                SELECT TOP 1 a.department_id
                FROM dbo.employee_assignments a
                WHERE a.employee_id = e.employee_id
                  AND a.effective_from <= ? AND (a.effective_to IS NULL OR a.effective_to > ?)
                ORDER BY a.effective_from DESC, a.assignment_id DESC
            ) a
            LEFT JOIN dbo.departments d ON d.department_id = a.department_id
            WHERE e.employment_status = 'DANG_LAM'
              AND e.hire_date <= ?
              AND NOT EXISTS (
                  SELECT 1 FROM dbo.contracts c
                  JOIN dbo.contract_salary_terms t ON t.contract_id = c.contract_id
                  WHERE c.employee_id = e.employee_id
                    AND c.status IN ('DA_KY', 'DA_KET_THUC', 'HET_HAN')
                    AND c.start_date <= ?
                    AND (c.end_date IS NULL OR c.end_date >= ?)
                    AND t.effective_from <= ?
                    AND (t.effective_to IS NULL OR t.effective_to >= ?)
              )
            ORDER BY e.employee_code
        """;
        jdbc.query(missingSalarySql, rs -> {
            String empCode = rs.getString("employee_code");
            String fullName = rs.getString("full_name");
            String deptName = rs.getString("dept_name");
            blockers.add(new PayrollBlockerItemDTO(
                    "THIEU_HOP_DONG",
                    "Thiếu điều khoản lương hợp đồng",
                    rs.getLong("employee_id"), empCode, fullName, deptName,
                    "HĐ-LƯƠNG", start + " -> " + end,
                    String.format("Nhân viên %s (Mã NV: %s — %s) chưa có hợp đồng đã ký hoặc điều khoản lương hiệu lực trong kỳ %s.",
                            fullName, empCode, deptName, period.getPeriodCode()),
                    "/contracts"
            ));
        }, end, start, end, end, start, end, start);

        List<PayrollBlockerItemDTO> calcBlockers = blockers.stream()
                .filter(b -> enforceCalculateWindow || !"CUA_SO_THOI_GIAN".equals(b.category()))
                .toList();

        List<PayrollBlockerItemDTO> lockBlockers = blockers.stream()
                .filter(b -> enforceLockWindow || !"CUA_SO_THOI_GIAN".equals(b.category()))
                .toList();

        boolean readyCalc = calcBlockers.isEmpty();
        boolean readyLock = lockBlockers.isEmpty();
        String summary = buildDetailedSummary(period, readyCalc ? lockBlockers : calcBlockers);

        return new PayrollReadinessReportDTO(
                batch != null ? batch.getBatchId() : null,
                period.getPeriodId(),
                period.getPeriodCode(),
                readyCalc,
                readyLock,
                summary,
                blockers
        );
    }

    private String buildDetailedSummary(PayrollPeriod period, List<PayrollBlockerItemDTO> activeBlockers) {
        if (activeBlockers == null || activeBlockers.isEmpty()) {
            return "Đủ điều kiện tính và chốt lương cho kỳ " + period.getPeriodCode() + ".";
        }
        String details = activeBlockers.stream()
                .limit(8)
                .map(b -> "• [" + b.categoryLabel() + "] " + b.detail())
                .collect(Collectors.joining("\n"));
        if (activeBlockers.size() > 8) {
            details += String.format("\n• ... và %d vướng mắc khác (xem danh sách nhân viên chi tiết bên dưới).", activeBlockers.size() - 8);
        }
        return "Chưa thể tự động tính/chốt lương kỳ " + period.getPeriodCode()
                + " do còn " + activeBlockers.size() + " vướng mắc ở các nhân viên sau:\n" + details;
    }
}
