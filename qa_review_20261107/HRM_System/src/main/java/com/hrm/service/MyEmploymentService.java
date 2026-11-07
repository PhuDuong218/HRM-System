package com.hrm.service;

import com.hrm.dto.*;
import com.hrm.enums.DecisionApprovalStatus;
import com.hrm.exception.BusinessException;
import com.hrm.model.Contract;
import com.hrm.model.EmployeeDecision;
import com.hrm.repository.ContractRepository;
import com.hrm.repository.ContractSalaryTermRepository;
import com.hrm.repository.EmployeeAllowanceRepository;
import com.hrm.repository.EmployeeDecisionRepository;
import com.hrm.security.AuthenticatedAccount;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class MyEmploymentService {
    private final ContractRepository contracts;
    private final ContractSalaryTermRepository salaryTerms;
    private final EmployeeAllowanceRepository allowances;
    private final EmployeeDecisionRepository decisions;

    public MyEmploymentService(ContractRepository contracts, ContractSalaryTermRepository salaryTerms,
            EmployeeAllowanceRepository allowances, EmployeeDecisionRepository decisions) {
        this.contracts = contracts;
        this.salaryTerms = salaryTerms;
        this.allowances = allowances;
        this.decisions = decisions;
    }

    public PageResponse<MyContractResponse> contracts(AuthenticatedAccount actor, int page, int size) {
        Long employeeId = employeeId(actor);
        var request = pageRequest(page, size, Sort.by(Sort.Direction.DESC, "startDate", "contractId"));
        return response(contracts.findByEmployee_EmployeeId(employeeId, request).map(this::contract));
    }

    public MyContractDetailResponse contractDetail(AuthenticatedAccount actor, Long id) {
        Long employeeId = employeeId(actor);
        var contract = contracts.findByContractIdAndEmployee_EmployeeId(id, employeeId)
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "Không tìm thấy hợp đồng"));
        var terms = salaryTerms
                .findByContract_ContractIdAndContract_Employee_EmployeeIdOrderByEffectiveFromAscSalaryTermIdAsc(id, employeeId)
                .stream().map(t -> new MyContractDetailResponse.SalaryTerm(t.getSalaryTermId(), t.getEffectiveFrom(),
                        t.getEffectiveTo(), t.getBaseSalary(), t.getPayBasis())).toList();
        return new MyContractDetailResponse(contract(contract), terms);
    }

    public List<MyAllowanceResponse> allowances(AuthenticatedAccount actor) {
        return allowances.findByEmployee_EmployeeIdOrderByEffectiveFromAsc(employeeId(actor)).stream()
                .map(a -> new MyAllowanceResponse(a.getEmployeeAllowanceId(), a.getAllowanceType().getAllowanceTypeId(),
                        a.getAllowanceType().getTypeCode(), a.getAllowanceType().getTypeName(), a.getEffectiveFrom(),
                        a.getEffectiveTo(), a.getCalculationBasis(), a.getAmountType(), a.getAmount())).toList();
    }

    public PageResponse<MyDecisionResponse> decisions(AuthenticatedAccount actor, int page, int size) {
        Long employeeId = employeeId(actor);
        var request = pageRequest(page, size, Sort.by(Sort.Direction.DESC, "issuedDate", "decisionId"));
        return response(decisions.findByEmployee_EmployeeIdAndApprovalStatus(
                employeeId, DecisionApprovalStatus.DA_DUYET, request).map(this::decision));
    }

    private Long employeeId(AuthenticatedAccount actor) {
        // The shared JWT filter checks account status, token version and current employment.
        if (actor == null || actor.accountId() == null)
            throw new BusinessException(HttpStatus.UNAUTHORIZED, "Phiên đăng nhập không hợp lệ");
        if (actor.employeeId() == null)
            throw new BusinessException(HttpStatus.FORBIDDEN, "Tài khoản chưa liên kết nhân viên");
        return actor.employeeId();
    }

    private PageRequest pageRequest(int page, int size, Sort sort) {
        if (page < 0 || size < 1 || size > 100 || (long) page * size > Integer.MAX_VALUE)
            throw new BusinessException(HttpStatus.BAD_REQUEST, "Phân trang không hợp lệ");
        return PageRequest.of(page, size, sort);
    }

    private <T> PageResponse<T> response(Page<T> page) {
        return new PageResponse<>(page.getContent(), page.getNumber(), page.getSize(),
                page.getTotalElements(), page.getTotalPages());
    }

    private MyContractResponse contract(Contract c) {
        var type = c.getContractType();
        return new MyContractResponse(c.getContractId(), c.getContractNumber(), type.getContractTypeId(),
                type.getTypeName(), type.getTermKind(), c.getProbationGroup(), c.getSignedDate(), c.getStartDate(),
                c.getEndDate(), c.getStatus(), c.getTerminationReasonType());
    }

    private MyDecisionResponse decision(EmployeeDecision d) {
        // Migration 011 appended a legacy payroll note to the same column as the decision text.
        String content = d.getContent();
        int legacyNote = content.indexOf("\n[011: lưu thông tin cũ; payroll_effect=");
        if (legacyNote >= 0) content = content.substring(0, legacyNote).stripTrailing();
        return new MyDecisionResponse(d.getDecisionId(), d.getDecisionNumber(), d.getDecisionType(), content,
                d.getIssuedDate(), d.getApprovalStatus(), d.getPayrollEffect(), d.getAmount(), d.getApplyDate(),
                d.getDisciplineForm(), d.getViolationDate(), d.getSalaryIncreaseDelayMonths());
    }
}
