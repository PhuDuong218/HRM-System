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

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
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

    @Value("${hrm.payroll.enforce-calculate-window:false}")
    private boolean enforceCalculateWindow;

    @Value("${hrm.payroll.enforce-lock-window:true}")
    private boolean enforceLockWindow;

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

    /**
     * Giữ tương thích với PayrollBatchService.lockBatch và các bài test tích hợp hiện có.
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

    public void requireReady(long batchId) {
        if (batchRepository == null || periodRepository == null) {
            throw new IllegalStateException("Chưa khởi tạo repository kiểm tra đợt lương.");
        }
        PayrollBatch batch = batchRepository.findById(batchId)
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "Không tìm thấy đợt bảng lương ID: " + batchId));
        PayrollPeriod period = periodRepository.findById(batch.getPeriodId())
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "Không tìm thấy kỳ lương ID: " + batch.getPeriodId()));
        assertReadyForLock(batch, period);
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

        if (!inWindow) {
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
            SELECT r.request_code, r.request_type, e.employee_id, e.employee_code, e.full_name,
                   COALESCE(d.department_name, N'Chưa phân công') AS dept_name,
                   CAST(COALESCE(ld.leave_start, r.submitted_at) AS date) AS ref_date
            FROM dbo.employee_requests r
            JOIN dbo.employees e ON e.employee_id = r.employee_id
            LEFT JOIN dbo.leave_request_details ld ON ld.request_id = r.request_id
            LEFT JOIN dbo.employee_assignments a ON a.employee_id = e.employee_id
                 AND a.effective_from <= ? AND (a.effective_to IS NULL OR a.effective_to > ?)
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
            blockers.add(new PayrollBlockerItemDTO(
                    "DON_NGHI_PHEP",
                    reqType + " chưa duyệt",
                    rs.getLong("employee_id"), empCode, fullName, deptName,
                    reqCode, refDate,
                    String.format("%s (%s - %s) có %s mã [%s] ngày %s đang ở trạng thái CHỜ DUYỆT.",
                            fullName, empCode, deptName, reqType, reqCode, refDate),
                    "/requests"
            ));
        }, end, start, endExclusiveDt, startDt, startDt, endExclusiveDt);

        // 3. ĐƠN TĂNG CA (OT) CHƯA DUYỆT HOẶC CHƯA XÁC NHẬN GIỜ THỰC TẾ
        String pendingOtSql = """
            SELECT o.request_code, o.status, o.confirmed_at, e.employee_id, e.employee_code, e.full_name,
                   COALESCE(d.department_name, N'Chưa phân công') AS dept_name,
                   CAST(o.requested_start AS date) AS work_date
            FROM dbo.overtime_requests o
            JOIN dbo.employees e ON e.employee_id = o.employee_id
            LEFT JOIN dbo.employee_assignments a ON a.employee_id = e.employee_id
                 AND a.effective_from <= ? AND (a.effective_to IS NULL OR a.effective_to > ?)
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
                    String.format("%s (%s - %s) có đơn OT [%s] ngày %s %s.",
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
            LEFT JOIN dbo.employee_assignments a ON a.employee_id = e.employee_id
                 AND a.effective_from <= ? AND (a.effective_to IS NULL OR a.effective_to > ?)
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
                    String.format("%s (%s - %s) có đơn giải trình công [%s] ngày %s chưa được xử lý.",
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
            LEFT JOIN dbo.employee_assignments a ON a.employee_id = e.employee_id
                 AND a.effective_from <= ? AND (a.effective_to IS NULL OR a.effective_to > ?)
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
                    String.format("%s (%s - %s) có quyết định [%s] áp dụng ngày %s đang CHỜ DUYỆT.",
                            fullName, empCode, deptName, decNum, applyDate),
                    "/decisions"
            ));
        }, end, start, start, end);

        // 6. LỊCH LÀM VIỆC / BẢN CHẤM CÔNG CHƯA XÁC NHẬN
        String unconfirmedAttSql = """
            SELECT e.employee_id, e.employee_code, e.full_name,
                   COALESCE(d.department_name, N'Chưa phân công') AS dept_name,
                   ws.work_date,
                   ar.verification_status
            FROM dbo.work_schedules ws
            JOIN dbo.employees e ON e.employee_id = ws.employee_id
            LEFT JOIN dbo.attendance_records ar ON ar.schedule_id = ws.schedule_id AND ar.invalidated_at IS NULL
            LEFT JOIN dbo.employee_assignments a ON a.employee_id = e.employee_id
                 AND a.effective_from <= ? AND (a.effective_to IS NULL OR a.effective_to > ?)
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
        }, end, start, start, end);

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
                    String.format("%s (%s - %s) còn %d ngày công chưa xác nhận: %s.",
                            meta[1], meta[0], meta[2], dates.size(), sampleDates),
                    "/attendance"
            ));
        }

        // 7. NHÂN VIÊN THIẾU HỢP ĐỒNG / ĐIỀU KHOẢN LƯƠNG HỢP LỆ
        String missingSalarySql = """
            SELECT e.employee_id, e.employee_code, e.full_name,
                   COALESCE(d.department_name, N'Chưa phân công') AS dept_name
            FROM dbo.employees e
            LEFT JOIN dbo.employee_assignments a ON a.employee_id = e.employee_id
                 AND a.effective_from <= ? AND (a.effective_to IS NULL OR a.effective_to > ?)
            LEFT JOIN dbo.departments d ON d.department_id = a.department_id
            WHERE e.employment_status = 'DANG_LAM'
              AND e.hire_date <= ?
              AND NOT EXISTS (
                  SELECT 1 FROM dbo.contracts c
                  JOIN dbo.contract_salary_terms t ON t.contract_id = c.contract_id
                  WHERE c.employee_id = e.employee_id
                    AND c.status = 'DA_KY'
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
                    String.format("%s (%s - %s) chưa có hợp đồng đã ký hoặc điều khoản lương hiệu lực trong kỳ %s.",
                            fullName, empCode, deptName, period.getPeriodCode()),
                    "/contracts"
            ));
        }, end, start, end, end, start);

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
        if (activeBlockers.isEmpty()) {
            return "Đủ điều kiện tính và chốt lương cho kỳ " + period.getPeriodCode() + ".";
        }
        String details = activeBlockers.stream()
                .limit(8)
                .map(b -> "• [" + b.categoryLabel() + "] " + b.detail())
                .collect(Collectors.joining("\n"));
        if (activeBlockers.size() > 8) {
            details += String.format("\n• ... và %d vướng mắc khác (xem chi tiết danh sách bên dưới).", activeBlockers.size() - 8);
        }
        return "Không thể hoàn tất tính/chốt lương kỳ " + period.getPeriodCode() + " vì các lý do sau:\n" + details;
    }
}