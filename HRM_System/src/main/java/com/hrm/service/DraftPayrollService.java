package com.hrm.service;

import com.hrm.constant.PayrollConstants;
import com.hrm.dto.SalaryRateSegment;
import com.hrm.enums.BatchStatus;
import com.hrm.enums.PayBasis;
import com.hrm.exception.BusinessException;
import com.hrm.model.PayrollDetail;
import com.hrm.model.PayrollLine;
import com.hrm.model.PayrollSnapshot;
import com.hrm.model.SalaryComponent;
import com.hrm.repository.*;
import com.hrm.security.AuthenticatedAccount;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

@Service
public class DraftPayrollService {
    private static final BigDecimal DAY_MINUTES = BigDecimal.valueOf(480);
    private static final BigDecimal HOUR_MINUTES = BigDecimal.valueOf(60);
    private static final BigDecimal STANDARD_DAYS = BigDecimal.valueOf(PayrollConstants.DEFAULT_STANDARD_WORK_DAYS);
    private static final BigDecimal OT_FACTOR = new BigDecimal("1.5");

    private record Day(long employeeId, LocalDate date) {}
    private record PayItem(String code, BigDecimal amount, String explanation) {}
    private record EmployeeDraft(PayrollInputPreparationService.EmployeeInput input, List<PayItem> items,
            BigDecimal paidMinutes, long overtimeMinutes) {}

    private final PayrollBatchRepository batches;
    private final PayrollPeriodRepository periods;
    private final PayrollDetailRepository details;
    private final PayrollLineRepository lines;
    private final PayrollSnapshotRepository snapshots;
    private final SalaryComponentRepository components;
    private final PayrollInputPreparationService preparation;
    private final ConfirmedAttendancePeriodService attendance;
    private final OvertimePayrollInputService overtime;
    private final JdbcTemplate jdbc;
    private final JsonMapper json;

    public DraftPayrollService(PayrollBatchRepository batches, PayrollPeriodRepository periods,
            PayrollDetailRepository details, PayrollLineRepository lines, PayrollSnapshotRepository snapshots,
            SalaryComponentRepository components, PayrollInputPreparationService preparation,
            ConfirmedAttendancePeriodService attendance, OvertimePayrollInputService overtime,
            JdbcTemplate jdbc, JsonMapper json) {
        this.batches = batches;
        this.periods = periods;
        this.details = details;
        this.lines = lines;
        this.snapshots = snapshots;
        this.components = components;
        this.preparation = preparation;
        this.attendance = attendance;
        this.overtime = overtime;
        this.jdbc = jdbc;
        this.json = json;
    }

    @Transactional
    public String calculate(Long batchId) {
        var actor = requireAdmin();
        var batch = batches.findForUpdate(batchId).orElseThrow(() -> conflict("Không tìm thấy đợt lương"));
        if (batch.getStatus() != BatchStatus.NHAP) throw conflict("Chỉ được tính đợt lương nháp");
        var period = periods.findById(batch.getPeriodId()).orElseThrow(() -> conflict("Không tìm thấy kỳ lương"));
        var from = period.getPeriodStart();
        var to = period.getPeriodEnd();
        var existing = details.findByBatchId(batchId);
        if (!existing.isEmpty()) {
            throw conflict("Đợt nháp đã có dữ liệu lương. Cần đối soát bản hiện tại trước khi tính lại.");
        }

        var standard = attendance.summarize(actor, from, to, null);
        var workByDay = standard.days().stream().collect(Collectors.toMap(
                d -> new Day(d.employeeId(), d.workDate()), Function.identity()));
        var otByDay = overtime.forPayrollPeriod(actor, batch.getPeriodId()).rows().stream().collect(
                Collectors.groupingBy(d -> new Day(d.employeeId(), d.workDate()),
                        Collectors.summingLong(d -> d.validMinutes())));
        var leaveByDay = paidLeaveMinutes(from, to);
        var input = preparation.prepareDraft(from, to);

        var issues = new TreeMap<String, String>();
        input.issues().forEach(issue -> issues.put(issue.employeeCode(), issue.message()));
        var drafts = new ArrayList<EmployeeDraft>();
        for (var employee : input.employees()) {
            try {
                drafts.add(calculateEmployee(employee, workByDay, otByDay, leaveByDay));
            } catch (BusinessException ex) {
                issues.put(employee.employeeCode(), ex.getMessage());
            }
        }
        if (drafts.isEmpty()) throw conflict("Chưa có nhân viên đủ dữ liệu để tính lương nháp. Cần đối soát: " + issues);

        var catalog = components.findAll().stream().collect(Collectors.toMap(
                SalaryComponent::getComponentCode, Function.identity()));
        for (var draft : drafts) {
            for (var item : draft.items()) {
                var component = catalog.get(item.code());
                if (component == null || !Boolean.TRUE.equals(component.getIsActive())
                        || !expectedDirection(item.code()).equals(component.getDirection().name())) {
                    throw conflict("Thiếu hoặc sai cấu hình thành phần lương: " + item.code());
                }
            }
        }

        var now = LocalDateTime.now(java.time.Clock.systemUTC());
        for (var draft : drafts) {
            var detail = new PayrollDetail(batchId, draft.input().employeeId());
            detail.setCreatedAt(now);
            detail.setCreatedBy(actor.accountId());
            detail = details.save(detail);
            int number = 1;
            for (var item : draft.items()) {
                var component = catalog.get(item.code());
                var line = new PayrollLine();
                line.setPayrollDetailId(detail.getPayrollDetailId());
                line.setLineNo(number++);
                line.setComponentId(component.getComponentId());
                line.setComponentCodeSnapshot(item.code());
                line.setComponentNameSnapshot(component.getComponentName());
                line.setDirectionSnapshot(component.getDirection().name());
                line.setAmount(item.amount());
                line.setExplanation(item.explanation());
                line.setCreatedAt(now);
                line.setCreatedBy(actor.accountId());
                lines.save(line);
            }
            var snapshot = new PayrollSnapshot();
            snapshot.setPayrollDetailId(detail.getPayrollDetailId());
            snapshot.setPolicyVersion("HRM-DRAFT-2026.1");
            snapshot.setCapturedAt(now);
            snapshot.setCreatedAt(now);
            snapshot.setCreatedBy(actor.accountId());
            snapshot.setInputSnapshotJson(json.writeValueAsString(Map.of(
                    "employee", draft.input(), "paidMinutes", draft.paidMinutes(),
                    "overtimeMinutes", draft.overtimeMinutes(), "standardDays", STANDARD_DAYS,
                    "minutesPerDay", DAY_MINUTES, "overtimeFactor", OT_FACTOR,
                    "periodStart", from, "periodEnd", to, "unresolvedEmployees", issues)));
            snapshots.save(snapshot);
        }
        return "Đã tính nháp cho " + drafts.size() + " nhân viên."
                + (issues.isEmpty() ? "" : " Cần đối soát dữ liệu: " + String.join(", ", issues.keySet()) + ".");
    }

    private EmployeeDraft calculateEmployee(PayrollInputPreparationService.EmployeeInput employee,
            Map<Day, ConfirmedAttendancePeriodService.EmployeeDay> workByDay, Map<Day, Long> otByDay,
            Map<Day, BigDecimal> leaveByDay) {
        BigDecimal workPay = BigDecimal.ZERO;
        BigDecimal otPay = BigDecimal.ZERO;
        BigDecimal paidMinutes = BigDecimal.ZERO;
        long overtimeMinutes = 0;
        var items = new ArrayList<PayItem>();
        for (SalaryRateSegment segment : employee.salarySegments()) {
            if (segment.baseSalary() == null || segment.baseSalary().signum() <= 0
                    || segment.payBasis() != PayBasis.THANG) {
                throw conflict("Chưa có mức lương tháng hợp lệ trong kỳ");
            }
            BigDecimal hourlyRate = segment.baseSalary().divide(
                    STANDARD_DAYS.multiply(BigDecimal.valueOf(8)), 12, RoundingMode.HALF_UP);
            for (LocalDate date = segment.from(); !date.isAfter(segment.to()); date = date.plusDays(1)) {
                var key = new Day(employee.employeeId(), date);
                var work = workByDay.get(key);
                if (work != null && work.unresolvedRecords() > 0) throw conflict("Công cần đối soát");
                BigDecimal worked = BigDecimal.valueOf(work == null ? 0 : work.netMinutes());
                BigDecimal paidLeave = leaveByDay.getOrDefault(key, BigDecimal.ZERO)
                        .min(DAY_MINUTES.subtract(worked).max(BigDecimal.ZERO));
                BigDecimal paid = worked.add(paidLeave);
                long ot = otByDay.getOrDefault(key, 0L);
                paidMinutes = paidMinutes.add(paid);
                overtimeMinutes += ot;
                workPay = workPay.add(hourlyRate.multiply(paid).divide(HOUR_MINUTES, 12, RoundingMode.HALF_UP));
                otPay = otPay.add(hourlyRate.multiply(OT_FACTOR).multiply(BigDecimal.valueOf(ot))
                        .divide(HOUR_MINUTES, 12, RoundingMode.HALF_UP));
            }
        }

        items.add(new PayItem("LUONG_CONG", money(workPay),
                "Phút công hưởng lương: " + paidMinutes.stripTrailingZeros().toPlainString()
                        + "; công chuẩn: " + STANDARD_DAYS));
        if (overtimeMinutes > 0) items.add(new PayItem("TANG_CA", money(otPay),
                overtimeMinutes + " phút OT được công nhận; hệ số nhân ×" + OT_FACTOR));

        BigDecimal taxable = money(workPay).add(money(otPay));
        BigDecimal insuranceBase = employee.salarySegments().getLast().baseSalary();
        for (var allowance : employee.allowances().items()) {
            BigDecimal amount = money(allowance.rawAmount());
            items.add(new PayItem("PHU_CAP", amount, "Phụ cấp " + allowance.typeCode()));
            if (allowance.isTaxable()) taxable = taxable.add(amount);
            if (allowance.countsForSocialInsurance()) insuranceBase = insuranceBase.add(amount);
        }
        for (var decision : employee.decisions().items()) {
            boolean addition = decision.direction() == com.hrm.enums.DecisionPayrollEffect.ADD;
            BigDecimal amount = money(decision.amount());
            items.add(new PayItem(addition ? "THUONG" : "KHAU_TRU", amount, "Quyết định " + decision.decisionNumber()));
            if (addition) taxable = taxable.add(amount);
        }

        BigDecimal insurance = money(insuranceBase.multiply(PayrollConstants.TOTAL_INSURANCE_RATE));
        items.add(new PayItem("BAO_HIEM", insurance, "Bảo hiểm tạm tính theo chính sách đồ án"));
        BigDecimal taxBase = taxable.subtract(insurance).subtract(PayrollConstants.GIAM_TRU_BAN_THAN);
        BigDecimal tax = money(progressiveTax(taxBase));
        if (tax.signum() > 0) items.add(new PayItem("THUE_TNCN", tax, "Thuế tạm tính theo chính sách đồ án"));
        return new EmployeeDraft(employee, List.copyOf(items), paidMinutes, overtimeMinutes);
    }

    private Map<Day, BigDecimal> paidLeaveMinutes(LocalDate from, LocalDate to) {
        Map<Day, BigDecimal> result = new HashMap<>();
        jdbc.query("""
                SELECT r.employee_id, s.work_date, SUM(a.charged_hours * 60) AS minutes
                FROM dbo.g2_leave_allocations a
                JOIN dbo.employee_requests r ON r.request_id = a.request_id
                JOIN dbo.leave_request_details d ON d.request_id = r.request_id
                JOIN dbo.leave_types t ON t.leave_type_id = d.leave_type_id
                JOIN dbo.work_schedules s ON s.schedule_id = a.schedule_id
                WHERE r.status = 'DA_DUYET' AND t.is_paid = 1 AND s.cancelled_at IS NULL
                  AND s.work_date BETWEEN ? AND ?
                  AND NOT EXISTS (SELECT 1 FROM dbo.employee_request_withdrawals w WHERE w.request_id = r.request_id)
                GROUP BY r.employee_id, s.work_date
                """, rs -> {
            result.put(new Day(rs.getLong("employee_id"), rs.getObject("work_date", LocalDate.class)),
                    rs.getBigDecimal("minutes"));
        }, from, to);
        return result;
    }

    private static BigDecimal progressiveTax(BigDecimal base) {
        long[] thresholds = {0, 5_000_000, 10_000_000, 18_000_000, 32_000_000, 52_000_000, 80_000_000};
        int[] rates = {5, 10, 15, 20, 25, 30, 35};
        BigDecimal tax = BigDecimal.ZERO;
        for (int i = 0; i < thresholds.length; i++) {
            BigDecimal lower = BigDecimal.valueOf(thresholds[i]);
            if (base.compareTo(lower) <= 0) break;
            BigDecimal upper = i + 1 < thresholds.length ? base.min(BigDecimal.valueOf(thresholds[i + 1])) : base;
            tax = tax.add(upper.subtract(lower).multiply(BigDecimal.valueOf(rates[i], 2)));
        }
        return tax;
    }

    private static String expectedDirection(String code) {
        return switch (code) {
            case "BAO_HIEM", "THUE_TNCN", "KHAU_TRU" -> "DEDUCT";
            default -> "ADD";
        };
    }

    private static AuthenticatedAccount requireAdmin() {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.isAuthenticated()
                && authentication.getPrincipal() instanceof AuthenticatedAccount actor
                && "ADMIN".equals(actor.roleCode())
                && authentication.getAuthorities().stream().anyMatch(a -> "PAYROLL_MANAGE".equals(a.getAuthority()))) {
            return actor;
        }
        throw new BusinessException(HttpStatus.FORBIDDEN, "Không có quyền tính lương");
    }

    private static BigDecimal money(BigDecimal amount) {
        return amount.setScale(0, RoundingMode.HALF_UP);
    }

    private static BusinessException conflict(String message) {
        return new BusinessException(HttpStatus.CONFLICT, message);
    }
}
