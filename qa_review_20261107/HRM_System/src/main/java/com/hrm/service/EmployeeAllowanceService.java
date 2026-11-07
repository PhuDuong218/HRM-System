package com.hrm.service;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import com.hrm.dto.*;
import com.hrm.enums.EmploymentStatus;
import com.hrm.enums.AllowanceAmountType;
import com.hrm.exception.BusinessException;
import com.hrm.model.*;
import com.hrm.repository.*;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Allowance periods include both effective dates. A null end means the period is open. */
@Service
@Transactional(readOnly = true)
public class EmployeeAllowanceService {
    private final EmployeeAllowanceRepository allowances;
    private final AllowanceTypeRepository types;
    private final EmployeeRepository employees;
    private final BusinessAuditService audit;
    private final PayrollHistoryGuard guard;
    private final Clock clock;
    @PersistenceContext
    private EntityManager em;

    public EmployeeAllowanceService(EmployeeAllowanceRepository allowances, AllowanceTypeRepository types,
            EmployeeRepository employees, BusinessAuditService audit, PayrollHistoryGuard guard, Clock clock) {
        this.allowances = allowances;
        this.types = types;
        this.employees = employees;
        this.audit = audit;
        this.guard = guard;
        this.clock = clock;
    }

    public List<EmployeeAllowanceResponse> list(Long employeeId) {
        if (!employees.existsById(employeeId))
            throw missing();
        return allowances.findByEmployee_EmployeeIdOrderByEffectiveFromAsc(employeeId).stream().map(this::dto).toList();
    }

    public EmployeeAllowanceResponse get(Long employeeId, Long id) {
        return dto(require(employeeId, id));
    }

    @Transactional
    public EmployeeAllowanceResponse create(Long employeeId, EmployeeAllowanceRequest r) {
        Employee employee = lockEmployee(employeeId);
        if (employee.getEmploymentStatus() != EmploymentStatus.DANG_LAM)
            throw bad("Chỉ gán mới cho nhân viên đang làm");
        AllowanceType type = activeType(r.allowanceTypeId());
        validateDates(r);
        var amountType = validateAmountType(r, AllowanceAmountType.FIXED);
        noOverlap(employeeId, r, null);
        guard.assertMutable(employeeId, r.effectiveFrom(), r.effectiveTo());
        var a = new EmployeeAllowance();
        a.setEmployee(employee);
        a.setAllowanceType(type);
        apply(a, r, amountType);
        a.setCreatedAt(LocalDateTime.now(clock));
        allowances.saveAndFlush(a);
        var after = dto(a);
        audit.record("employee_allowances", a.getEmployeeAllowanceId(), "ALLOWANCE_ASSIGN", r.reason().trim(), null,
                after);
        return after;
    }

    @Transactional
    public EmployeeAllowanceResponse updateFuture(Long employeeId, Long id, EmployeeAllowanceRequest r) {
        lockEmployee(employeeId);
        var a = refresh(employeeId, id);
        assertFuture(a);
        if (!r.effectiveFrom().isAfter(LocalDate.now(clock)))
            throw bad("Bản sửa cũng phải ở tương lai");
        sameType(a, r);
        activeType(r.allowanceTypeId());
        validateDates(r);
        var amountType = validateAmountType(r, a.getAmountType());
        noOverlap(employeeId, r, id);
        guard.assertMutable(employeeId, a.getEffectiveFrom(), a.getEffectiveTo());
        guard.assertMutable(employeeId, r.effectiveFrom(), r.effectiveTo());
        var before = dto(a);
        apply(a, r, amountType);
        a.setUpdatedAt(LocalDateTime.now(clock));
        allowances.saveAndFlush(a);
        var after = dto(a);
        audit.record("employee_allowances", id, "ALLOWANCE_UPDATE", r.reason().trim(), before, after);
        return after;
    }

    @Transactional
    public void deleteFuture(Long employeeId, Long id, String reason) {
        lockEmployee(employeeId);
        var a = refresh(employeeId, id);
        assertFuture(a);
        validateReason(reason);
        deleteAssignment(a, reason);
    }

    private void deleteAssignment(EmployeeAllowance a, String reason) {
        guard.assertMutable(a.getEmployee().getEmployeeId(), a.getEffectiveFrom(), a.getEffectiveTo());
        var before = dto(a);
        allowances.delete(a);
        allowances.flush();
        audit.record("employee_allowances", a.getEmployeeAllowanceId(), "ALLOWANCE_DELETE", reason.trim(), before, null);
    }

    @Transactional
    public EmployeeAllowanceResponse changeFrom(Long employeeId, Long id, AllowanceChangeRequest request) {
        lockEmployee(employeeId);
        var old = refresh(employeeId, id);
        var r = request.newAllowance();
        sameType(old, r);
        activeType(r.allowanceTypeId());
        validateDates(r);
        var amountType = validateAmountType(r, old.getAmountType());
        if (r.effectiveFrom().isBefore(LocalDate.now(clock)) || !r.effectiveFrom().isAfter(old.getEffectiveFrom())) {
            throw bad("Ngày đổi phải từ hôm nay và sau ngày bắt đầu cũ");
        }
        if (!Objects.equals(r.effectiveTo(), old.getEffectiveTo()))
            throw bad("Giữ ngày cuối của khoảng cũ khi đổi mức");
        LocalDate end = r.effectiveFrom().minusDays(1);
        noOverlap(employeeId, r, id);
        guard.assertMutable(employeeId, r.effectiveFrom(), r.effectiveTo());
        var before = dto(old);
        old.setEffectiveTo(end);
        old.setUpdatedAt(LocalDateTime.now(clock));
        allowances.saveAndFlush(old);
        var fresh = new EmployeeAllowance();
        fresh.setEmployee(old.getEmployee());
        fresh.setAllowanceType(old.getAllowanceType());
        apply(fresh, r, amountType);
        fresh.setCreatedAt(LocalDateTime.now(clock));
        allowances.saveAndFlush(fresh);
        var after = dto(fresh);
        audit.record("employee_allowances", id, "ALLOWANCE_CLOSE", r.reason().trim(), before, dto(old));
        audit.record("employee_allowances", fresh.getEmployeeAllowanceId(), "ALLOWANCE_ASSIGN",
                "Thay mức từ ID=" + id + "; " + r.reason().trim(), null, after);
        return after;
    }

    /** stopFrom is the first excluded day, so the previous day can equal effectiveFrom. */
    @Transactional
    public EmployeeAllowanceResponse endFrom(Long employeeId, Long id, AllowanceEndRequest r) {
        lockEmployee(employeeId);
        var a = refresh(employeeId, id);
        if (r.stopFrom().isBefore(LocalDate.now(clock)))
            throw bad("Không ngừng hưởng hồi tố");
        LocalDate end = r.stopFrom().minusDays(1);
        if (end.isBefore(a.getEffectiveFrom()))
            throw bad("Ngày ngừng phải sau ngày bắt đầu để giữ ít nhất một ngày hưởng phụ cấp");
        if (a.getEffectiveTo() != null && !end.isBefore(a.getEffectiveTo()))
            throw bad("Ngày ngừng phải rút ngắn khoảng cũ");
        return endAssignment(a, r.stopFrom(), r.reason());
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void endAllFrom(Long employeeId, LocalDate date, String reason) {
        if (employeeId == null || date == null) {
            throw bad("Cần nhân viên và ngày ngừng hưởng phụ cấp");
        }
        validateReason(reason);
        lockEmployee(employeeId);
        var affected = allowances.findByEmployee_EmployeeIdOrderByEffectiveFromAsc(employeeId).stream()
                .filter(a -> a.getEffectiveTo() == null || !a.getEffectiveTo().isBefore(date))
                .toList();
        for (var assignment : affected) {
            if (assignment.getEffectiveFrom().isBefore(date)) {
                endAssignment(assignment, date, reason);
            } else {
                deleteAssignment(assignment, reason);
            }
        }
    }

    private EmployeeAllowanceResponse endAssignment(EmployeeAllowance a, LocalDate stopFrom, String reason) {
        guard.assertMutable(a.getEmployee().getEmployeeId(), stopFrom, a.getEffectiveTo());
        var before = dto(a);
        a.setEffectiveTo(stopFrom.minusDays(1));
        a.setUpdatedAt(LocalDateTime.now(clock));
        allowances.saveAndFlush(a);
        var after = dto(a);
        audit.record("employee_allowances", a.getEmployeeAllowanceId(), "ALLOWANCE_END", reason.trim(), before, after);
        return after;
    }

    private Employee lockEmployee(Long id) {
        return employees.findByIdForUpdate(id).orElseThrow(this::missing);
    }

    private AllowanceType activeType(Long id) {
        var type = types.findByIdForUpdate(id).orElseThrow(this::missing);
        em.refresh(type);
        if (!Boolean.TRUE.equals(type.getIsActive()))
            throw bad("Loại phụ cấp đã ngừng dùng");
        return type;
    }

    private EmployeeAllowance require(Long employeeId, Long id) {
        return allowances.findByEmployeeAllowanceIdAndEmployee_EmployeeId(id, employeeId).orElseThrow(this::missing);
    }

    private EmployeeAllowance refresh(Long employeeId, Long id) {
        var a = require(employeeId, id);
        em.refresh(a);
        return a;
    }

    private void sameType(EmployeeAllowance a, EmployeeAllowanceRequest r) {
        if (!a.getAllowanceType().getAllowanceTypeId().equals(r.allowanceTypeId()))
            throw bad("Không đổi loại/nhân viên của bản đã gán");
    }

    private void assertFuture(EmployeeAllowance a) {
        if (!a.getEffectiveFrom().isAfter(LocalDate.now(clock)))
            throw bad("Không sửa/xóa bản đã hiệu lực; dùng change-from/end-from");
    }

    private void validateDates(EmployeeAllowanceRequest r) {
        if (r.effectiveTo() != null && r.effectiveTo().isBefore(r.effectiveFrom()))
            throw bad("Ngày kết thúc phải từ ngày bắt đầu trở đi.");
    }

    private void validateReason(String r) {
        if (r == null || r.isBlank() || r.length() > 500)
            throw bad("Cần lý do <=500 ký tự");
    }

    private void noOverlap(Long employeeId, EmployeeAllowanceRequest r, Long exceptId) {
        if (allowances.countOverlapping(employeeId, r.allowanceTypeId(), r.effectiveFrom(), r.effectiveTo(),
                exceptId) > 0) {
            throw new BusinessException(HttpStatus.CONFLICT, "Trùng khoảng phụ cấp cùng loại");
        }
    }

    private AllowanceAmountType validateAmountType(EmployeeAllowanceRequest request, AllowanceAmountType currentType) {
        if (request.amountType() == null && currentType == AllowanceAmountType.PERCENT_BASE_SALARY)
            throw bad("Cần gửi amountType khi sửa hoặc đổi mức phụ cấp theo phần trăm");
        var amountType = request.amountType() == null ? AllowanceAmountType.FIXED : request.amountType();
        if (amountType == AllowanceAmountType.PERCENT_BASE_SALARY
                && (request.amount() == null || request.amount().signum() < 0
                    || request.amount().compareTo(new BigDecimal("100")) > 0))
            throw bad("Tỷ lệ phụ cấp phải từ 0 đến 100%");
        return amountType;
    }

    private void apply(EmployeeAllowance a, EmployeeAllowanceRequest r, AllowanceAmountType amountType) {
        a.setEffectiveFrom(r.effectiveFrom());
        a.setEffectiveTo(r.effectiveTo());
        a.setCalculationBasis(r.calculationBasis());
        a.setAmount(r.amount());
        a.setAmountType(amountType);
        a.setConditionNote(r.conditionNote());
    }

    private EmployeeAllowanceResponse dto(EmployeeAllowance a) {
        return new EmployeeAllowanceResponse(a.getEmployeeAllowanceId(), a.getEmployee().getEmployeeId(),
                a.getAllowanceType().getAllowanceTypeId(), a.getAllowanceType().getTypeCode(),
                a.getAllowanceType().getTypeName(),
                a.getEffectiveFrom(), a.getEffectiveTo(), a.getCalculationBasis(), a.getAmount(), a.getConditionNote(),
                a.getAmountType());
    }

    private BusinessException missing() {
        return new BusinessException(HttpStatus.NOT_FOUND, "Không tìm thấy nhân viên/loại/khoản phụ cấp phù hợp");
    }

    private BusinessException bad(String m) {
        return new BusinessException(HttpStatus.BAD_REQUEST, m);
    }
}
