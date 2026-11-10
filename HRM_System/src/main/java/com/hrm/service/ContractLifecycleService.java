package com.hrm.service;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import com.hrm.dto.*;
import com.hrm.enums.ContractStatus;
import com.hrm.enums.TerminationReasonType;
import com.hrm.exception.BusinessException;
import com.hrm.model.Contract;
import com.hrm.repository.ContractRepository;
import com.hrm.repository.EmployeeRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
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
        if (old.getStatus()==ContractStatus.DA_HUY) throw bad("Không gia hạn hợp đồng đã hủy");
        if (old.getEndDate()==null) throw bad("Hợp đồng vô thời hạn không dùng gia hạn");
        if (audit.hasAction("contracts",id,"CONTRACT_TERMINATE")) throw bad("Bản đã chấm dứt không dùng gia hạn");
        if (audit.hasAction("contracts",id,"CONTRACT_RENEW")) throw bad("Bản này đã gia hạn");
        var next=service.prepare(request.newContract());
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
        if (request==null || request.terminationDate()==null || request.reasonType()==null ||
                request.reason()==null || request.reason().isBlank() || request.reason().length()>500) {
            throw new BusinessException(HttpStatus.BAD_REQUEST,"Cần ngày, loại chấm dứt và lý do từ 1 đến 500 ký tự");
        }
        Contract c=lock(id);
        LocalDate date=request.terminationDate();
        if (c.getStatus()!=ContractStatus.DA_KY || c.getTerminationReasonType()!=null ||
                audit.hasAction("contracts",id,"CONTRACT_TERMINATE")) {
            throw bad("Hợp đồng đã kết thúc hoặc đã ghi nhận chấm dứt");
        }
        if (audit.hasAction("contracts",id,"CONTRACT_RENEW")) {
            throw bad("Đã có bản gia hạn; cần xử lý quan hệ gia hạn trước khi rút ngắn bản cũ");
        }
        if (date.isBefore(c.getStartDate()) ||
                (c.getEndDate()!=null && date.isAfter(c.getEndDate()))) throw bad("Ngày chấm dứt ngoài thời hạn");
        return recordTermination(c,date,request.reasonType(),request.reason());
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void terminateForResignation(Long employeeId, LocalDate lastDay, TerminationReasonType reasonType,
            String reason) {
        if (employeeId == null || lastDay == null || reasonType == null || reason == null || reason.isBlank()
                || reason.length() > 500) {
            throw new BusinessException(HttpStatus.BAD_REQUEST,
                    "Cần nhân viên, ngày làm cuối, loại chấm dứt và lý do từ 1 đến 500 ký tự");
        }
        lockEmployee(employeeId);
        for (Contract contract : contracts.findByEmployee_EmployeeIdOrderByStartDateAscContractIdAsc(employeeId)) {
            if (contract.getStatus() != ContractStatus.DA_KY
                    || (contract.getEndDate() != null && !contract.getEndDate().isAfter(lastDay))) {
                continue;
            }
            if (contract.getStartDate().isAfter(lastDay)) {
                cancelForResignation(contract, reasonType, reason);
            } else {
                recordTermination(contract, lastDay, reasonType, reason);
            }
        }
    }

    private void cancelForResignation(Contract contract, TerminationReasonType reasonType, String reason) {
        guard.assertMutable(contract.getEmployee().getEmployeeId(), contract.getStartDate(), contract.getEndDate());
        Long id = contract.getContractId();
        var before = service.getOne(id);
        contract.setStatus(ContractStatus.DA_HUY);
        contract.setTerminationReasonType(reasonType);
        contract.setUpdatedAt(LocalDateTime.now(clock));
        contracts.saveAndFlush(contract);
        audit.record("contracts", id, "CONTRACT_CANCEL", reason.trim(), before, service.getOne(id));
    }

    private ContractResponse recordTermination(Contract c, LocalDate date, TerminationReasonType reasonType,
            String reason) {
        Long id = c.getContractId();
        var before=service.getOne(id);
        // Ngày chấm dứt vẫn có hiệu lực. Chỉ bảo vệ khoảng thực sự bị rút ngắn.
        if (c.getEndDate()==null || date.isBefore(c.getEndDate())) {
            guard.assertMutable(c.getEmployee().getEmployeeId(),date.plusDays(1),c.getEndDate());
        }
        c.setEndDate(date);
        c.setTerminationReasonType(reasonType);
        c.setUpdatedAt(LocalDateTime.now(clock));
        if (date.isBefore(LocalDate.now(clock))) c.setStatus(ContractStatus.DA_KET_THUC);
        // Hôm nay/tương lai vẫn còn hiệu lực đến hết ngày cuối; job đóng vào ngày tiếp theo.
        contracts.saveAndFlush(c);
        var after=service.getOne(id);
        audit.record("contracts",id,"CONTRACT_TERMINATE",reason.trim(),before,after);
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
        // Preserve the reason of an API termination scheduled for a future date.
        // Legacy API terminations without a reason remain NULL; do not invent natural expiry.
        if (c.getTerminationReasonType()==null && !audit.hasAction("contracts",id,"CONTRACT_TERMINATE")) {
            c.setTerminationReasonType(TerminationReasonType.HET_HAN);
        }
        c.setStatus(ContractStatus.DA_KET_THUC); c.setUpdatedAt(LocalDateTime.now(clock));
        contracts.saveAndFlush(c);
        audit.record("contracts",id,"CONTRACT_EXPIRE","Qua ngày cuối hiệu lực",before,service.getOne(id));
    }

    private Contract lock(Long id) {
        Contract c=contracts.findById(id).orElseThrow(() ->
                new BusinessException(HttpStatus.NOT_FOUND,"Không có hợp đồng"));
        lockEmployee(c.getEmployee().getEmployeeId());
        em.refresh(c); return c;
    }

    private void lockEmployee(Long employeeId) {
        employees.findByIdForUpdate(employeeId).orElseThrow(() ->
                new BusinessException(HttpStatus.NOT_FOUND,"Không có nhân viên"));
    }
    private BusinessException bad(String message) { return new BusinessException(HttpStatus.CONFLICT,message); }
}
