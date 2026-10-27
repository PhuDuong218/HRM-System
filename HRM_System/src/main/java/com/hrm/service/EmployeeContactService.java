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

        Employee employee = employeeRepository
                .findByIdForUpdate(employeeId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Không tìm thấy nhân viên: " + employeeId));

        Map<String, String> before = snapshot(employee);

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

        auditService.recordContactUpdate(
                actorAccountId,
                employeeId,
                before,
                after);

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