package com.hrm.service;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import com.hrm.dto.*;
import com.hrm.enums.ContractStatus;
import com.hrm.exception.BusinessException;
import com.hrm.model.Contract;
import com.hrm.repository.ContractRepository;
import com.hrm.repository.EmployeeRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ContractLifecycleService {
    private final ContractRepository contracts;
    private final EmployeeRepository employees;
    private final ContractService service;
    private final BusinessAuditService audit;
    private final PayrollHistoryGuard guard;
    private final Clock clock;
    @PersistenceContext private EntityManager em;

    public ContractLifecycleService(ContractRepository contracts,EmployeeRepository employees,
            ContractService service,BusinessAuditService audit,PayrollHistoryGuard guard,Clock clock) {
        this.contracts=contracts; this.employees=employees; this.service=service;
        this.audit=audit; this.guard=guard; this.clock=clock;
    }

    @Transactional
    public ContractResponse renew(Long id,ContractRenewRequest request) {
        Contract old=lock(id);
        LocalDate today=LocalDate.now(clock);
        if (old.getEndDate()==null) throw bad("Hợp đồng vô thời hạn không dùng gia hạn");
        if (audit.hasAction("contracts",id,"CONTRACT_TERMINATE")) throw bad("Bản đã chấm dứt không dùng gia hạn");
        if (audit.hasAction("contracts",id,"CONTRACT_RENEW")) throw bad("Bản này đã gia hạn");
        var next=request.newContract();
        if (!old.getEmployee().getEmployeeId().equals(next.employeeId())) throw bad("Gia hạn phải cùng nhân viên");
        if (!next.startDate().isAfter(old.getEndDate())) throw bad("Bản mới phải bắt đầu sau ngày cuối bản cũ");
        // Nếu không hết hạn tự nhiên mà status kết thúc sớm thì không suy đoán nguyên nhân.
        if (old.getStatus()==ContractStatus.DA_KET_THUC && !old.getEndDate().isBefore(today)) {
            throw bad("Trạng thái và ngày hợp đồng cũ không phù hợp");
        }
        var before=service.getOne(id);
        guard.assertMutable(next.employeeId(),next.startDate(),next.endDate());
        // Bean khác nên @Transactional của create tham gia cùng transaction, không tự commit riêng.
        var fresh=service.create(next);
        if (old.getEndDate().isBefore(today)) {
            old.setStatus(ContractStatus.DA_KET_THUC);
            old.setUpdatedAt(LocalDateTime.now(clock)); contracts.saveAndFlush(old);
        }
        audit.record("contracts",id,"CONTRACT_RENEW",
                "Hợp đồng mới ID="+fresh.contractId()+"; "+request.reason().trim(),before,service.getOne(id));
        return fresh;
    }

    @Transactional
    public ContractResponse terminate(Long id,ContractTerminateRequest request) {
        Contract c=lock(id);
        LocalDate today=LocalDate.now(clock), date=request.terminationDate();
        if (c.getStatus()!=ContractStatus.DA_KY || audit.hasAction("contracts",id,"CONTRACT_TERMINATE")) {
            throw bad("Hợp đồng đã kết thúc hoặc đã ghi nhận chấm dứt");
        }
        if (audit.hasAction("contracts",id,"CONTRACT_RENEW")) {
            throw bad("Đã có bản gia hạn; cần xử lý quan hệ gia hạn trước khi rút ngắn bản cũ");
        }
        if (date.isBefore(today)) throw bad("Không chấm dứt hồi tố");
        if (today.isBefore(c.getStartDate()) || date.isBefore(c.getStartDate()) ||
                (c.getEndDate()!=null && date.isAfter(c.getEndDate()))) throw bad("Ngày chấm dứt ngoài thời hạn");
        var before=service.getOne(id);
        // Chỉ các ngày sau ngày cuối mới mất hiệu lực; không đổi nguồn cho ngày hôm nay.
        if (c.getEndDate()==null || date.isBefore(c.getEndDate())) {
            guard.assertMutable(c.getEmployee().getEmployeeId(),date.plusDays(1),c.getEndDate());
        }
        c.setEndDate(date); c.setUpdatedAt(LocalDateTime.now(clock));
        // Ngày cuối vẫn còn hiệu lực. Job đổi status từ ngày tiếp theo.
        contracts.saveAndFlush(c);
        var after=service.getOne(id);
        audit.record("contracts",id,"CONTRACT_TERMINATE",request.reason().trim(),before,after);
        return after;
    }

    @Transactional(readOnly=true)
    public List<Long> expiredIds() {
        return contracts.findExpiredIds(LocalDate.now(clock),PageRequest.of(0,100));
    }

    @Transactional
    public void closeExpired(Long id) {
        Contract c=lock(id);
        if (c.getStatus()!=ContractStatus.DA_KY || c.getEndDate()==null ||
                !c.getEndDate().isBefore(LocalDate.now(clock))) return;
        var before=service.getOne(id);
        c.setStatus(ContractStatus.DA_KET_THUC); c.setUpdatedAt(LocalDateTime.now(clock));
        contracts.saveAndFlush(c);
        audit.record("contracts",id,"CONTRACT_EXPIRE","Qua ngày cuối hiệu lực",before,service.getOne(id));
    }

    private Contract lock(Long id) {
        Contract c=contracts.findById(id).orElseThrow(() ->
                new BusinessException(HttpStatus.NOT_FOUND,"Không có hợp đồng"));
        employees.findByIdForUpdate(c.getEmployee().getEmployeeId()).orElseThrow(() ->
                new BusinessException(HttpStatus.NOT_FOUND,"Không có nhân viên"));
        em.refresh(c); return c;
    }
    private BusinessException bad(String message) { return new BusinessException(HttpStatus.CONFLICT,message); }
}