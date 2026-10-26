package com.hrm.service;

import java.util.LinkedHashMap;
import java.util.Map;

import com.hrm.dto.EmployeeDTO;
import com.hrm.dto.EmployeeRequestDTO;
import com.hrm.dto.EmployeeUpdateRequestDTO;
import com.hrm.exception.ResourceNotFoundException;
import com.hrm.model.Employee;
import com.hrm.repository.EmployeeRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class EmployeeCreationAuditService {

    private final EmployeeService employeeService;
    private final EmployeeRepository employeeRepository;
    private final EmployeeAuditService auditService;

    public EmployeeCreationAuditService(
            EmployeeService employeeService,
            EmployeeRepository employeeRepository,
            EmployeeAuditService auditService) {

        this.employeeService = employeeService;
        this.employeeRepository = employeeRepository;
        this.auditService = auditService;
    }

    @Transactional
    public EmployeeDTO create(
            EmployeeRequestDTO request,
            Long actorAccountId) {

        if (actorAccountId == null || actorAccountId <= 0) {
            throw new IllegalArgumentException(
                    "Phải xác định tài khoản tạo nhân viên");
        }

        // Giữ lại toàn bộ kiểm tra và nghiệp vụ tạo đang có.
        EmployeeDTO result = employeeService.addEmployee(request);

        Employee employee = employeeRepository
                .findByIdForUpdate(result.getEmployeeId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Không tìm thấy nhân viên vừa tạo"));

        employee.setCreatedBy(actorAccountId);

        // Phân công ban đầu cũng được tạo bởi tài khoản này.
        employee.getAssignments().forEach(
                assignment -> assignment.setCreatedBy(actorAccountId));

        employeeRepository.flush();

        Map<String, String> after = snapshot(employee);

        after.put(
                "departmentId",
                text(result.getCurrentDepartmentId()));

        after.put(
                "positionId",
                text(result.getCurrentPositionId()));

        auditService.recordCreation(
                actorAccountId,
                employee.getEmployeeId(),
                after);

        return result;
    }

    /** Legacy PUT replaces its optional fields; address remains managed by contact PATCH. */
    @Transactional
    public EmployeeDTO update(Long id, EmployeeUpdateRequestDTO request, Long actorAccountId) {
        if (actorAccountId == null || actorAccountId <= 0) {
            throw new IllegalArgumentException("Phải xác định tài khoản cập nhật nhân viên");
        }
        Employee employee = employeeRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy nhân viên: " + id));
        Map<String, String> before = snapshot(employee);
        employee.setUpdatedBy(actorAccountId);
        EmployeeDTO result = employeeService.updateEmployee(id, request);
        employeeRepository.flush();
        auditService.recordProfileUpdate(actorAccountId, id, before, snapshot(employee));
        return result;
    }

    private Map<String, String> snapshot(Employee employee) {
        Map<String, String> data = new LinkedHashMap<>();

        data.put("employeeCode", employee.getEmployeeCode());
        data.put("fullName", employee.getFullName());
        data.put("birthDate", text(employee.getBirthDate()));
        data.put("hireDate", text(employee.getHireDate()));

        data.put(
                "employmentStatus",
                text(employee.getEmploymentStatus()));

        data.put("email", employee.getEmail());
        data.put("phone", employee.getPhone());
        data.put("address", employee.getAddress());
        data.put("identityNumber", employee.getIdentityNumber());

        return data;
    }

    private String text(Object value) {
        return value == null ? null : value.toString();
    }
}
