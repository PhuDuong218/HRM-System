package com.hrm.service;

import java.security.Principal;
import java.util.LinkedHashMap;
import java.util.Map;

import com.hrm.dto.EmployeeContactPatchDTO;
import com.hrm.dto.EmployeeDetailDTO;
import com.hrm.exception.ResourceNotFoundException;
import com.hrm.model.Employee;
import com.hrm.repository.EmployeeRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class EmployeeContactService {

    private final EmployeeRepository employeeRepository;
    private final EmployeeService employeeService;
    private final DepartmentHeadAccessService accessService;
    private final EmployeeAuditService auditService;

    public EmployeeContactService(
            EmployeeRepository employeeRepository,
            EmployeeService employeeService,
            DepartmentHeadAccessService accessService,
            EmployeeAuditService auditService) {

        this.employeeRepository = employeeRepository;
        this.employeeService = employeeService;
        this.accessService = accessService;
        this.auditService = auditService;
    }

    @Transactional
    public EmployeeDetailDTO patchContact(
            Long employeeId,
            EmployeeContactPatchDTO request,
            Principal principal) {

        // ID tài khoản được xác định từ đăng nhập phía server.
        Long actorAccountId = accessService.requireManager(principal);

        return updateContact(employeeId, request, actorAccountId);
    }

    @Transactional
    public void updateOwnContact(com.hrm.security.AuthenticatedAccount actor, EmployeeContactPatchDTO request) {
        if (actor == null || actor.employeeId() == null || !java.util.Set.of("ADMIN", "HR", "EMPLOYEE").contains(actor.roleCode()))
            throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.FORBIDDEN);
        if (request.getIdentityNumber() != null)
            throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.BAD_REQUEST, "Không được tự sửa CCCD");
        updateContact(actor.employeeId(), request, actor.accountId());
    }

    private EmployeeDetailDTO updateContact(Long employeeId, EmployeeContactPatchDTO request, Long actorAccountId) {
        Employee employee = employeeRepository
                .findByIdForUpdate(employeeId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Không tìm thấy nhân viên: " + employeeId));

        Map<String, String> before = snapshot(employee);
        EmployeeWriteRules.requireVersion(employee,request.getVersion());
        request.setReason(EmployeeWriteRules.reason(request.getReason()));
        EmployeeWriteRules.requiredIfPresent(request.hasEmail(),request.getEmail(),"Email");
        EmployeeWriteRules.requiredIfPresent(request.hasAddress(),request.getAddress(),"Địa chỉ");
        EmployeeWriteRules.requiredIfPresent(request.hasIdentityNumber(),request.getIdentityNumber(),"CCCD/CMND");
        EmployeeWriteRules.identity(request.getIdentityNumber());

        // Tính dữ liệu dự kiến sau cập nhật để phát hiện request không đổi gì.
        Map<String, String> proposed = new LinkedHashMap<>(before);

        putIfSupplied(proposed, "email", request.getEmail());
        putIfSupplied(proposed, "phone", request.getPhone());
        putIfSupplied(proposed, "address", request.getAddress());
        putIfSupplied(
                proposed, "identityNumber", request.getIdentityNumber());

        if (before.equals(proposed)) {
            return employeeService.getEmployeeDetailById(employeeId);
        }

        employee.setUpdatedBy(actorAccountId);

        EmployeeDetailDTO result =
                employeeService.patchEmployeeContact(employeeId, request);

        // Chụp dữ liệu thực tế sau khi service cập nhật.
        Map<String, String> after = snapshot(employee);

        auditService.recordContactUpdate(actorAccountId,employeeId,before,after,request.getReason());

        return result;
    }

    private Map<String, String> snapshot(Employee employee) {
        Map<String, String> data = new LinkedHashMap<>();

        data.put("email", employee.getEmail());
        data.put("phone", employee.getPhone());
        data.put("address", employee.getAddress());
        data.put("identityNumber", employee.getIdentityNumber());

        return data;
    }

    private void putIfSupplied(
            Map<String, String> data,
            String field,
            String value) {

        if (value != null) {
            data.put(field, value.isBlank() ? null : value.trim());
        }
    }
}
