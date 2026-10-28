package com.hrm.service;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import com.hrm.dto.*;
import com.hrm.enums.ContractStatus;
import com.hrm.exception.BusinessException;
import com.hrm.model.Contract;
import com.hrm.model.ContractSalaryTerm;
import com.hrm.repository.*;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly=true)
public class ContractSalaryTermService {
    private final ContractRepository contracts;
    private final EmployeeRepository employees;
    private final ContractSalaryTermRepository terms;
    private final PayrollHistoryGuard guard;
    private final BusinessAuditService audit;
    private final Clock clock;
    @PersistenceContext private EntityManager em;

    public ContractSalaryTermService(ContractRepository contracts, EmployeeRepository employees,
        ContractSalaryTermRepository terms, PayrollHistoryGuard guard,
        BusinessAuditService audit, Clock clock) {
        this.contracts=contracts; this.employees=employees; this.terms=terms;
        this.guard=guard; this.audit=audit; this.clock=clock;
    }

    public List<SalaryTermResponse> list(Long contractId) {
        requireContract(contractId);
        return terms.findByContract_ContractIdOrderByEffectiveFromAsc(contractId)
                .stream().map(this::dto).toList();
    }

    public SalaryTermResponse get(Long contractId, Long id) {
        requireContract(contractId);
        return dto(requireTerm(contractId,id));
    }

    @Transactional
    public SalaryTermResponse create(Long contractId, SalaryTermRequest request) {
        Contract c=lockContract(contractId);
        validate(c,request);
        assertNoOverlap(c,request,null);
        guard.assertMutable(c.getEmployee().getEmployeeId(), request.effectiveFrom(),
                applicableEnd(c,request.effectiveTo()));
        ContractSalaryTerm t=new ContractSalaryTerm();
        t.setContract(c); apply(t,request); t.setCreatedAt(LocalDateTime.now(clock));
        terms.saveAndFlush(t);
        var after=dto(t);
        audit.record("contract_salary_terms", t.getSalaryTermId(), "SALARY_TERM_CREATE",
                request.reason().trim(), null, after);
        return after;
    }

    @Transactional
    public SalaryTermResponse updateFuture(Long contractId, Long id, SalaryTermRequest request) {
        Contract c=lockContract(contractId);
        ContractSalaryTerm t=requireTerm(contractId,id); em.refresh(t);
        assertFuture(t);
        if (!request.effectiveFrom().isAfter(LocalDate.now(clock))) {
            throw error("Bản sửa cũng phải bắt đầu trong tương lai");
        }
        validate(c,request); assertNoOverlap(c,request,id);
        guard.assertMutable(c.getEmployee().getEmployeeId(), t.getEffectiveFrom(),
                applicableEnd(c,t.getEffectiveTo()));
        guard.assertMutable(c.getEmployee().getEmployeeId(), request.effectiveFrom(),
                applicableEnd(c,request.effectiveTo()));
        var before=dto(t);
        apply(t,request); t.setUpdatedAt(LocalDateTime.now(clock)); terms.saveAndFlush(t);
        var after=dto(t);
        audit.record("contract_salary_terms",id,"SALARY_TERM_UPDATE",request.reason().trim(),before,after);
        return after;
    }

    @Transactional
    public void deleteFuture(Long contractId, Long id, String reason) {
        Contract c=lockContract(contractId);
        ContractSalaryTerm t=requireTerm(contractId,id); em.refresh(t); assertFuture(t);
        if (reason==null || reason.isBlank() || reason.length()>500) throw error("Cần lý do <=500 ký tự");
        guard.assertMutable(c.getEmployee().getEmployeeId(),t.getEffectiveFrom(),applicableEnd(c,t.getEffectiveTo()));
        var before=dto(t); terms.delete(t); terms.flush();
        audit.record("contract_salary_terms",id,"SALARY_TERM_DELETE",reason.trim(),before,null);
    }

    @Transactional
    public SalaryTermResponse changeRate(Long contractId, Long id, SalaryTermChangeRequest request) {
        Contract c=lockContract(contractId);
        ContractSalaryTerm old=requireTerm(contractId,id); em.refresh(old);
        SalaryTermRequest next=request.newTerm(); validate(c,next);
        if (next.effectiveFrom().isBefore(LocalDate.now(clock))) throw error("Không đổi lương hồi tố");
        if (!next.effectiveFrom().isAfter(old.getEffectiveFrom())) throw error("Ngày mới phải sau ngày bắt đầu cũ");
        if (!Objects.equals(next.effectiveTo(),old.getEffectiveTo())) {
            throw error("Đổi lương giữ ngày kết thúc của khoảng cũ");
        }
        LocalDate oldEnd=next.effectiveFrom().minusDays(1);
        if (!oldEnd.isAfter(old.getEffectiveFrom())) {
            throw error("Khoảng cũ còn một ngày, SQL hiện không hỗ trợ; cần thống nhất migration");
        }
        // validate đã bảo đảm khoảng mới đúng; bước này cũng bắt ngày mới sau end cũ.
        assertNoOverlap(c,next,id);
        guard.assertMutable(c.getEmployee().getEmployeeId(),next.effectiveFrom(),applicableEnd(c,next.effectiveTo()));
        var before=dto(old);
        old.setEffectiveTo(oldEnd); old.setUpdatedAt(LocalDateTime.now(clock)); terms.saveAndFlush(old);
        ContractSalaryTerm fresh=new ContractSalaryTerm(); fresh.setContract(c); apply(fresh,next);
        fresh.setCreatedAt(LocalDateTime.now(clock)); terms.saveAndFlush(fresh);
        var after=dto(fresh);
        audit.record("contract_salary_terms",id,"SALARY_TERM_CLOSE",next.reason().trim(),before,dto(old));
        audit.record("contract_salary_terms",fresh.getSalaryTermId(),"SALARY_TERM_CREATE",
                "Thay mức từ ID="+id+"; "+next.reason().trim(),null,after);
        return after;
    }

    private Contract requireContract(Long id) {
        return contracts.findById(id).orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND,"Không có hợp đồng"));
    }
    private Contract lockContract(Long id) {
        Contract c=requireContract(id);
        employees.findByIdForUpdate(c.getEmployee().getEmployeeId()).orElseThrow(() ->
                new BusinessException(HttpStatus.NOT_FOUND,"Không có nhân viên"));
        em.refresh(c);
        return c;
    }
    private ContractSalaryTerm requireTerm(Long contractId,Long id) {
        return terms.findBySalaryTermIdAndContract_ContractId(id,contractId).orElseThrow(() ->
                new BusinessException(HttpStatus.NOT_FOUND,"Không có mức lương thuộc hợp đồng này"));
    }
    private void assertFuture(ContractSalaryTerm t) {
        if (!t.getEffectiveFrom().isAfter(LocalDate.now(clock))) {
            throw error("Không sửa/xóa mức đã hiệu lực; dùng change-rate để tạo dòng mới");
        }
    }
    private void validate(Contract c,SalaryTermRequest r) {
        if (c.getStatus()!=ContractStatus.DA_KY) throw error("Chỉ ghi mức lương cho hợp đồng DA_KY");
        if (r.effectiveFrom().isBefore(c.getStartDate())) throw error("Mức lương bắt đầu trước hợp đồng");
        if (r.effectiveTo()!=null && !r.effectiveTo().isAfter(r.effectiveFrom())) throw error("Ngày kết thúc phải lớn hơn bắt đầu");
        if (c.getEndDate()!=null && (r.effectiveFrom().isAfter(c.getEndDate()) ||
                (r.effectiveTo()!=null && r.effectiveTo().isAfter(c.getEndDate())))) {
            throw error("Khoảng lương vượt thời hạn hợp đồng");
        }
        // effectiveTo null được phép: phạm vi áp dụng vẫn bị giới hạn bởi endDate hợp đồng ở D-08.
    }
    private void assertNoOverlap(Contract c,SalaryTermRequest r,Long exceptId) {
        if (terms.countOverlapping(c.getContractId(),r.effectiveFrom(),r.effectiveTo(),exceptId)>0) {
            throw new BusinessException(HttpStatus.CONFLICT,"Khoảng mức lương bị chồng");
        }
    }
    private LocalDate applicableEnd(Contract c,LocalDate end) {
        if (end==null) return c.getEndDate();
        return c.getEndDate()==null || end.isBefore(c.getEndDate()) ? end : c.getEndDate();
    }
    private void apply(ContractSalaryTerm t,SalaryTermRequest r) {
        t.setEffectiveFrom(r.effectiveFrom()); t.setEffectiveTo(r.effectiveTo());
        t.setBaseSalary(r.baseSalary()); t.setPayBasis(r.payBasis()); t.setDocumentReference(r.documentReference());
    }
    private SalaryTermResponse dto(ContractSalaryTerm t) {
        return new SalaryTermResponse(t.getSalaryTermId(),t.getContract().getContractId(),
                t.getEffectiveFrom(),t.getEffectiveTo(),t.getBaseSalary(),t.getPayBasis(),t.getDocumentReference());
    }
    private BusinessException error(String message) { return new BusinessException(HttpStatus.BAD_REQUEST,message); }
}