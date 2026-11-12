package com.hrm.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Locale;
import com.hrm.dto.*;
import com.hrm.enums.ContractStatus;
import com.hrm.enums.EmploymentStatus;
import com.hrm.exception.BusinessException;
import com.hrm.model.Contract;
import com.hrm.model.ContractType;
import com.hrm.model.Employee;
import com.hrm.repository.ContractRepository;
import com.hrm.repository.ContractTypeRepository;
import com.hrm.repository.EmployeeRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class ContractService {
    @org.springframework.beans.factory.annotation.Autowired private PersonnelScopeService personnelScope;
    @org.springframework.beans.factory.annotation.Autowired private ContractNumberService numbers;
    private final ContractRepository contracts;
    private final ContractTypeRepository types;
    private final EmployeeRepository employees;
    private final ContractAuditService audit;
    private final PayrollHistoryGuard historyGuard;
    private final ContractTermPolicy termPolicy;

    public ContractService(ContractRepository contracts, ContractTypeRepository types,
            EmployeeRepository employees, ContractAuditService audit,
            PayrollHistoryGuard historyGuard, ContractTermPolicy termPolicy) {
        this.contracts = contracts;
        this.types = types;
        this.employees = employees;
        this.audit = audit;
        this.historyGuard = historyGuard; this.termPolicy=termPolicy;
    }

    public java.util.List<ContractAuditService.HistoryEntry> history(Long id) {
        require(id);
        return audit.history(id);
    }

    public ContractResponse getOne(Long id) {
        return toResponse(require(id));
    }

    public PageResponse<ContractResponse> search(
            Long employeeId, ContractStatus status, String q, LocalDate from, LocalDate to,
            int page, int size) {
        if (page < 0 || size < 1 || size > 100) {
            throw error(HttpStatus.BAD_REQUEST, "page >= 0; size từ 1 đến 100");
        }
        if (employeeId != null && employeeId <= 0) {
            throw error(HttpStatus.BAD_REQUEST, "employeeId phải dương");
        }
        if (q != null && q.length() > 50) {
            throw error(HttpStatus.BAD_REQUEST, "Từ khóa tối đa 50 ký tự");
        }
        if (from != null && to != null && from.isAfter(to)) {
            throw error(HttpStatus.BAD_REQUEST, "Từ ngày phải nhỏ hơn hoặc bằng đến ngày.");
        }
        // Escape wildcard để tìm theo nội dung literal, gồm cả '[' của SQL Server.
        String term = q == null ? "" : q.trim().toLowerCase(Locale.ROOT);
        String pattern = "%" + term.replace("!", "!!").replace("%", "!%")
                .replace("_", "!_").replace("[", "![") + "%";
        var pageable = PageRequest.of(page, size,
                Sort.by(Sort.Direction.DESC, "startDate", "contractId"));
        var result = PersonnelScopeService.isHr()
                ? contracts.searchScoped(employeeId, status, pattern, from, to, personnelScope.employees(), pageable)
                : contracts.search(employeeId, status, pattern, from, to, pageable);
        return new PageResponse<>(result.getContent().stream().map(this::toResponse).toList(),
                result.getNumber(), result.getSize(),
                result.getTotalElements(), result.getTotalPages());
    }

    @Transactional
    public ContractResponse create(ContractCreateRequest request) {
        Employee employee = lockEmployee(request.employeeId());
        request = prepare(request);

        historyGuard.assertMutable(request.employeeId(), request.startDate(), request.endDate());

        Contract saved = createRecord(employee, request);
        audit.record("CONTRACT_CREATE", saved.getContractId(),
                "Ghi nhận hợp đồng đã ký", null, snapshot(saved));
        return toResponse(saved);
    }

    public ContractCreateRequest prepare(ContractCreateRequest request){
        ContractType type=types.findById(request.contractTypeId()).orElseThrow(()->error(HttpStatus.NOT_FOUND,"Không tìm thấy loại hợp đồng"));
        return new ContractCreateRequest(request.employeeId(),request.contractTypeId(),null,request.signedDate(),request.startDate(),ContractGeneratedFields.endDate(type.getTermKind(),type.getMaxMonths(),request.startDate(),request.endDate()),request.probationGroup());
    }

    private Contract createRecord(Employee employee, ContractCreateRequest request) {
        if (employee.getEmploymentStatus() != EmploymentStatus.DANG_LAM) {
            throw error(HttpStatus.CONFLICT, "Nhân viên không còn đang làm việc");
        }
        if (request.endDate() != null && request.endDate().isBefore(request.startDate())) {
            throw error(HttpStatus.BAD_REQUEST, "Ngày kết thúc không được trước ngày bắt đầu");
        }
        ContractType type = types.findById(request.contractTypeId())
                .orElseThrow(() -> error(HttpStatus.NOT_FOUND, "Không tìm thấy loại hợp đồng"));
        if (!Boolean.TRUE.equals(type.getIsActive())) {
            throw error(HttpStatus.CONFLICT, "Loại hợp đồng đã ngừng sử dụng");
        }
        if (contracts.countOverlapping(employee.getEmployeeId(),
                request.startDate(), request.endDate()) > 0) {
            throw error(HttpStatus.CONFLICT, "Thời hạn chồng với hợp đồng đã có");
        }
        termPolicy.validate(employee.getEmployeeId(),type,request);
        String number = numbers.next(type.getTypeCode());
        if(contracts.existsByContractNumber(number))throw error(HttpStatus.CONFLICT,"Số hợp đồng đã tồn tại; cần kiểm tra bộ cấp số");
        Contract entity = new Contract();
        entity.setEmployee(employee);
        entity.setProbationGroup(request.probationGroup());
        entity.setContractType(type);
        entity.setContractNumber(number);
        entity.setSignedDate(request.signedDate());
        entity.setStartDate(request.startDate());
        entity.setEndDate(request.endDate());
        entity.setStatus(ContractStatus.DA_KY);
        entity.setCreatedAt(LocalDateTime.now());
        return contracts.saveAndFlush(entity);
    }

    private Employee lockEmployee(Long id) {
        return employees.findByIdForUpdate(id)
                .orElseThrow(() -> error(HttpStatus.NOT_FOUND, "Không tìm thấy nhân viên"));
    }

    private Contract require(Long id) {
        return contracts.findById(id).orElseThrow(() -> error(HttpStatus.NOT_FOUND, "Không tìm thấy hợp đồng"));
    }

    private BusinessException error(HttpStatus status, String message) {
        return new BusinessException(status, message);
    }

    private ContractResponse toResponse(Contract c) {
        return new ContractResponse(c.getContractId(), c.getEmployee().getEmployeeId(),
                c.getEmployee().getEmployeeCode(), c.getEmployee().getFullName(),
                c.getContractType().getContractTypeId(), c.getContractType().getTypeName(),
                c.getContractNumber(), c.getSignedDate(), c.getStartDate(),
                c.getEndDate(), c.getStatus(), c.getContractType().getTermKind(), c.getProbationGroup(),
                c.getTerminationReasonType());
    }

    // JSON chỉ chứa ID, LocalDate và enum do hệ thống quản lý; không nối chuỗi
    // người dùng.
    private String snapshot(Contract c) {
        return "{\"contractId\":" + c.getContractId()
                + ",\"employeeId\":" + c.getEmployee().getEmployeeId()
                + ",\"startDate\":\"" + c.getStartDate() + "\""
                + ",\"endDate\":" + (c.getEndDate() == null ? "null" : "\"" + c.getEndDate() + "\"")
                + ",\"status\":\"" + c.getStatus().name() + "\"}";
    }

}
