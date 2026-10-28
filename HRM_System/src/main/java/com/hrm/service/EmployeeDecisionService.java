package com.hrm.service;

import java.time.*;
import java.util.Objects;
import com.hrm.dto.*;
import com.hrm.enums.*;
import com.hrm.exception.BusinessException;
import com.hrm.model.*;
import com.hrm.repository.*;
import org.springframework.data.domain.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class EmployeeDecisionService {
    private final EmployeeDecisionRepository decisions;
    private final EmployeeRepository employees;
    private final DecisionActorProvider actors;
    private final DecisionAuditService audit;
    private final PayrollHistoryGuard history;
    private final DecisionPayrollPolicy policy;
    private final Clock clock;

    public EmployeeDecisionService(EmployeeDecisionRepository decisions, EmployeeRepository employees,
            DecisionActorProvider actors, DecisionAuditService audit, PayrollHistoryGuard history,
            DecisionPayrollPolicy policy, Clock clock) {
        this.decisions = decisions;
        this.employees = employees;
        this.actors = actors;
        this.audit = audit;
        this.history = history;
        this.policy = policy;
        this.clock = clock;
    }

    public DecisionResponse get(Long id) {
        actors.requireManager();
        return dto(require(id));
    }

    public PageResponse<DecisionResponse> list(Long employeeId, DecisionApprovalStatus status, int page, int size) {
        actors.requireManager();
        if (page < 0 || size < 1 || size > 100 || employeeId != null && employeeId <= 0)
            throw error(HttpStatus.BAD_REQUEST, "page>=0; size 1..100; employeeId phải dương");
        var rows = decisions.search(employeeId, status, PageRequest.of(page, size,
                Sort.by(Sort.Direction.DESC, "issuedDate", "decisionId")));
        return new PageResponse<>(rows.getContent().stream().map(this::dto).toList(),
                rows.getNumber(), rows.getSize(), rows.getTotalElements(), rows.getTotalPages());
    }

    @Transactional
    public DecisionResponse create(DecisionRequest r) {
        var actor = actors.requireManager();
        var employee = lockEmployee(r.employeeId());
        validate(r);
        if (decisions.existsByDecisionNumber(r.decisionNumber().trim()))
            throw error(HttpStatus.CONFLICT, "Số quyết định đã tồn tại");
        var d = new EmployeeDecision();
        d.setEmployee(employee);
        copy(d, r);
        d.setIssuedBy(actor.accountId());
        d.setApprovalStatus(DecisionApprovalStatus.CHO_DUYET);
        d.setCreatedAt(LocalDateTime.now(clock));
        d.setCreatedBy(actor.accountId());
        decisions.saveAndFlush(d);
        var after = dto(d);
        audit.record(actor.accountId(), d.getDecisionId(), "DECISION_CREATE", r.reason().trim(), null, after);
        return after;
    }

    @Transactional
    public DecisionResponse update(Long id, DecisionRequest r) {
        var actor = actors.requireManager();
        var d = lock(id);
        pending(d);
        validate(r);
        if (!d.getEmployee().getEmployeeId().equals(r.employeeId())
                || !d.getDecisionNumber().equals(r.decisionNumber().trim()))
            throw error(HttpStatus.CONFLICT, "Không đổi nhân viên hoặc số quyết định sau khi tạo");
        var before = dto(d);
        copy(d, r);
        touch(d, actor);
        decisions.saveAndFlush(d);
        var after = dto(d);
        audit.record(actor.accountId(), id, "DECISION_UPDATE", r.reason().trim(), before, after);
        return after;
    }

    @Transactional
    public void delete(Long id, String reason) {
        var actor = actors.requireManager();
        var d = lock(id);
        pending(d);
        if (reason == null || reason.isBlank() || reason.length() > 500)
            throw error(HttpStatus.BAD_REQUEST, "Cần lý do xóa từ 1 đến 500 ký tự");
        var before = dto(d);
        decisions.delete(d);
        decisions.flush();
        audit.record(actor.accountId(), id, "DECISION_DELETE", reason.trim(), before, null);
    }

    @Transactional
    public DecisionResponse approve(Long id, DecisionActionRequest r) {
        return review(id, r, true);
    }

    @Transactional
    public DecisionResponse reject(Long id, DecisionActionRequest r) {
        return review(id, r, false);
    }

    private DecisionResponse review(Long id, DecisionActionRequest r, boolean approve) {
        var actor = actors.requireManager();
        var d = lock(id);
        pending(d);
        if (Objects.equals(actor.accountId(), d.getIssuedBy()) ||
                Objects.equals(actor.employeeId(), d.getEmployee().getEmployeeId()))
            throw error(HttpStatus.FORBIDDEN, "Không tự xử lý quyết định do mình ban hành hoặc áp dụng cho mình");
        if (approve && d.getPayrollEffect() != DecisionPayrollEffect.NONE) {
            policy.requireConfirmed();
            history.assertMutable(d.getEmployee().getEmployeeId(), d.getApplyDate(), d.getApplyDate());
        }
        var before = dto(d);
        d.setApprovalStatus(approve ? DecisionApprovalStatus.DA_DUYET : DecisionApprovalStatus.TU_CHOI);
        d.setApprovedBy(actor.accountId());
        d.setApprovedAt(LocalDateTime.now(clock));
        touch(d, actor);
        decisions.saveAndFlush(d);
        var after = dto(d);
        audit.record(actor.accountId(), id, approve ? "DECISION_APPROVE" : "DECISION_REJECT", r.reason().trim(), before,
                after);
        return after;
    }

    private void validate(DecisionRequest r) {
        if (r.issuedDate().isAfter(LocalDate.now(clock)))
            throw error(HttpStatus.BAD_REQUEST, "Ngày ban hành không được ở tương lai");
        if (r.payrollEffect() == DecisionPayrollEffect.NONE) {
            if (r.amount() != null || r.applyDate() != null)
                throw error(HttpStatus.BAD_REQUEST, "NONE yêu cầu amount và applyDate là null");
        } else if (r.amount() == null || r.amount().signum() <= 0 || r.applyDate() == null) {
            throw error(HttpStatus.BAD_REQUEST, "ADD/DEDUCT cần amount dương và applyDate");
        } else if (r.applyDate().isBefore(r.issuedDate())) {
            throw error(HttpStatus.BAD_REQUEST, "Ngày áp dụng không trước ngày ban hành");
        }
    }

    private void copy(EmployeeDecision d, DecisionRequest r) {
        d.setDecisionNumber(r.decisionNumber().trim());
        d.setDecisionType(r.decisionType());
        d.setContent(r.content().trim());
        d.setIssuedDate(r.issuedDate());
        d.setPayrollEffect(r.payrollEffect());
        d.setAmount(r.amount());
        d.setApplyDate(r.applyDate());
    }

    private void touch(EmployeeDecision d, DecisionActor a) {
        d.setUpdatedAt(LocalDateTime.now(clock));
        d.setUpdatedBy(a.accountId());
    }

    private EmployeeDecision require(Long id) {
        return decisions.findById(id).orElseThrow(() -> error(HttpStatus.NOT_FOUND, "Không có quyết định"));
    }

    private Employee lockEmployee(Long id) {
        return employees.findByIdForUpdate(id).orElseThrow(() -> error(HttpStatus.NOT_FOUND, "Không có nhân viên"));
    }

    private EmployeeDecision lock(Long id) {
        // Cùng thứ tự khóa với D10/D11: nhân viên trước rồi bản ghi nghiệp vụ.
        Long employeeId = decisions.findEmployeeIdById(id)
                .orElseThrow(() -> error(HttpStatus.NOT_FOUND, "Không có quyết định"));
        lockEmployee(employeeId);
        return decisions.findByIdForUpdate(id).orElseThrow(() -> error(HttpStatus.NOT_FOUND, "Không có quyết định"));
    }

    private void pending(EmployeeDecision d) {
        if (d.getApprovalStatus() != DecisionApprovalStatus.CHO_DUYET)
            throw error(HttpStatus.CONFLICT, "Chỉ sửa/xóa/xử lý quyết định đang CHO_DUYET");
    }

    private DecisionResponse dto(EmployeeDecision d) {
        return new DecisionResponse(d.getDecisionId(), d.getEmployee().getEmployeeId(), d.getDecisionNumber(),
                d.getDecisionType(), d.getContent(), d.getIssuedDate(), d.getIssuedBy(), d.getApprovalStatus(),
                d.getApprovedBy(), d.getApprovedAt(), d.getPayrollEffect(), d.getAmount(), d.getApplyDate());
    }

    private BusinessException error(HttpStatus s, String m) {
        return new BusinessException(s, m);
    }
}