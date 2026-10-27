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
    private final ContractRepository contracts;
    private final ContractTypeRepository types;
    private final EmployeeRepository employees;
    private final ContractAuditService audit;

    public ContractService(ContractRepository contracts, ContractTypeRepository types,
                           EmployeeRepository employees, ContractAuditService audit) {
        this.contracts = contracts;
        this.types = types;
        this.employees = employees;
        this.audit = audit;
    }

    public ContractResponse getOne(Long id) {
        return toResponse(require(id));
    }

    public PageResponse<ContractResponse> search(
            Long employeeId, ContractStatus status, String q, int page, int size) {
        if (page < 0 || size < 1 || size > 100) {
            throw error(HttpStatus.BAD_REQUEST, "page >= 0; size từ 1 đến 100");
        }
        if (employeeId != null && employeeId <= 0) {
            throw error(HttpStatus.BAD_REQUEST, "employeeId phải dương");
        }
        if (q != null && q.length() > 50) {
            throw error(HttpStatus.BAD_REQUEST, "Từ khóa tối đa 50 ký tự");
        }
        // Escape wildcard để tìm theo nội dung literal, gồm cả '[' của SQL Server.
        String term = q == null ? "" : q.trim().toLowerCase(Locale.ROOT);
        String pattern = "%" + term.replace("!", "!!").replace("%", "!%")
                .replace("_", "!_").replace("[", "![") + "%";
        var pageable = PageRequest.of(page, size,
                Sort.by(Sort.Direction.DESC, "startDate", "contractId"));
        var result = contracts.search(employeeId, status, pattern, pageable);
        return new PageResponse<>(result.getContent().stream().map(this::toResponse).toList(),
                result.getNumber(), result.getSize(),
                result.getTotalElements(), result.getTotalPages());
    }

    @Transactional
    public ContractResponse create(ContractCreateRequest request) {
        Employee employee = lockEmployee(request.employeeId());
        Contract saved = createRecord(employee, request);
        audit.record("CONTRACT_CREATE", saved.getContractId(),
                "Ghi nhận hợp đồng đã ký", null, snapshot(saved));
        return toResponse(saved);
    }

    @Transactional
    public ContractResponse renew(Long id, ContractRenewRequest request) {
        // Đọc employeeId trước, nhưng khóa cha trước mọi thay đổi nghiệp vụ.
        Long employeeId = require(id).getEmployee().getEmployeeId();
        Employee employee = lockEmployee(employeeId);
        // Refresh để đọc trạng thái mới nhất sau khi chờ khóa cha.
        Contract old = refreshContract(id);
        if (old.getEndDate() == null || !old.getEndDate().isBefore(LocalDate.now())) {
            throw error(HttpStatus.CONFLICT,
                    "Bài mẫu chỉ gia hạn hợp đồng có ngày kết thúc đã qua");
        }
        ContractCreateRequest next = request.newContract();
        if (!employeeId.equals(next.employeeId())) {
            throw error(HttpStatus.BAD_REQUEST, "Gia hạn phải cùng nhân viên");
        }
        if (!next.startDate().isAfter(old.getEndDate())) {
            throw error(HttpStatus.BAD_REQUEST, "Ngày mới phải sau ngày kết thúc bản cũ");
        }
        // Không gia hạn hai lần từ cùng bản cũ trong bài mẫu.
        if (wasRenewed(id)) {
            throw error(HttpStatus.CONFLICT, "Hợp đồng này đã được gia hạn");
        }
        String before = snapshot(old);
        // createRecord kiểm tra trạng thái nhân viên, FK, số duy nhất và giao thoa.
        Contract saved = createRecord(employee, next);
        old.setStatus(ContractStatus.DA_KET_THUC);
        old.setUpdatedAt(LocalDateTime.now());
        contracts.saveAndFlush(old);
        audit.record("CONTRACT_RENEW", id,
                "Hợp đồng mới ID=" + saved.getContractId() + "; " + request.reason().trim(),
                before, snapshot(old));
        audit.record("CONTRACT_CREATE", saved.getContractId(),
                "Gia hạn từ ID=" + id, null, snapshot(saved));
        return toResponse(saved);
    }

    @Transactional
    public ContractResponse terminate(Long id, ContractTerminateRequest request) {
        Long employeeId = require(id).getEmployee().getEmployeeId();
        lockEmployee(employeeId);
        Contract entity = refreshContract(id);
        LocalDate date = request.terminationDate();
        if (!date.equals(LocalDate.now())) {
            throw error(HttpStatus.BAD_REQUEST, "Bài mẫu chỉ chấm dứt vào ngày hôm nay");
        }
        if (entity.getStatus() != ContractStatus.DA_KY) {
            throw error(HttpStatus.CONFLICT, "Chỉ chấm dứt hợp đồng DA_KY");
        }
        if (date.isBefore(entity.getStartDate()) ||
                (entity.getEndDate() != null && date.isAfter(entity.getEndDate()))) {
            throw error(HttpStatus.BAD_REQUEST, "Ngày chấm dứt ngoài thời hạn hợp đồng");
        }
        if (contracts.countSalaryTerms(id) > 0) {
            throw error(HttpStatus.CONFLICT,
                    "Đã có điều khoản lương; cần quy trình D-07 trước khi chấm dứt");
        }
        String before = snapshot(entity);
        entity.setEndDate(date);
        entity.setStatus(ContractStatus.DA_KET_THUC);
        entity.setUpdatedAt(LocalDateTime.now());
        contracts.saveAndFlush(entity);
        audit.record("CONTRACT_TERMINATE", id, request.reason().trim(),
                before, snapshot(entity));
        return toResponse(entity);
    }

    private Contract createRecord(Employee employee, ContractCreateRequest request) {
        if (employee.getEmploymentStatus() != EmploymentStatus.DANG_LAM) {
            throw error(HttpStatus.CONFLICT, "Nhân viên không còn đang làm việc");
        }
        if (request.endDate() != null && request.endDate().isBefore(request.startDate())) {
            throw error(HttpStatus.BAD_REQUEST, "Ngày kết thúc không được trước ngày bắt đầu");
        }
        ContractType type = types.findById(request.contractTypeId()).orElseThrow(() ->
                error(HttpStatus.NOT_FOUND, "Không tìm thấy loại hợp đồng"));
        if (!Boolean.TRUE.equals(type.getIsActive())) {
            throw error(HttpStatus.CONFLICT, "Loại hợp đồng đã ngừng sử dụng");
        }
        String number = request.contractNumber().trim();
        if (contracts.existsByContractNumber(number)) {
            throw error(HttpStatus.CONFLICT, "Số hợp đồng đã tồn tại");
        }
        if (contracts.countOverlapping(employee.getEmployeeId(),
                request.startDate(), request.endDate()) > 0) {
            throw error(HttpStatus.CONFLICT, "Thời hạn chồng với hợp đồng đã có");
        }
        Contract entity = new Contract();
        entity.setEmployee(employee);
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
        return employees.findByIdForUpdate(id).orElseThrow(() ->
                error(HttpStatus.NOT_FOUND, "Không tìm thấy nhân viên"));
    }

    private Contract require(Long id) {
        return contracts.findById(id).orElseThrow(() ->
                error(HttpStatus.NOT_FOUND, "Không tìm thấy hợp đồng"));
    }

    private Contract refreshContract(Long id) {
        Contract entity = require(id);
        entityManager.refresh(entity);
        return entity;
    }

    private boolean wasRenewed(Long id) {
        return audit.hasRenewal(id);
    }

    private BusinessException error(HttpStatus status, String message) {
        return new BusinessException(status, message);
    }

    private ContractResponse toResponse(Contract c) {
        return new ContractResponse(c.getContractId(), c.getEmployee().getEmployeeId(),
                c.getEmployee().getEmployeeCode(), c.getEmployee().getFullName(),
                c.getContractType().getContractTypeId(), c.getContractType().getTypeName(),
                c.getContractNumber(), c.getSignedDate(), c.getStartDate(),
                c.getEndDate(), c.getStatus());
    }

    // JSON chỉ chứa ID, LocalDate và enum do hệ thống quản lý; không nối chuỗi người dùng.
    private String snapshot(Contract c) {
        return "{\"contractId\":" + c.getContractId()
                + ",\"employeeId\":" + c.getEmployee().getEmployeeId()
                + ",\"startDate\":\"" + c.getStartDate() + "\""
                + ",\"endDate\":" + (c.getEndDate() == null ? "null" : "\"" + c.getEndDate() + "\"")
                + ",\"status\":\"" + c.getStatus().name() + "\"}";
    }

    @jakarta.persistence.PersistenceContext
    private jakarta.persistence.EntityManager entityManager;
}