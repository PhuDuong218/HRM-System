package com.hrm.service;

import java.util.LinkedHashMap;
import java.util.Map;

import com.hrm.exception.ResourceNotFoundException;
import com.hrm.model.Employee;
import com.hrm.repository.EmployeeRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class EmployeeTerminationAuditService {

    private final EmployeeRepository employeeRepository;
    private final EmployeeService employeeService;
    private final EmployeeAuditService auditService;
    private final AssignmentGuardService guard;

    public EmployeeTerminationAuditService(
            EmployeeRepository employeeRepository,
            EmployeeService employeeService,
            EmployeeAuditService auditService,
            AssignmentGuardService guard) {

        this.employeeRepository = employeeRepository;
        this.employeeService = employeeService;
        this.auditService = auditService;
        this.guard = guard;
    }

    @Transactional
    public void terminate(Long employeeId, Long actorAccountId) {
        if (actorAccountId == null || actorAccountId <= 0) {
            throw new IllegalArgumentException(
                    "Phải xác định tài khoản thực hiện");
        }

        // Giữ đúng thứ tự khóa của các luồng thay đổi phân công.
        guard.lockTimeline();

        Employee employee = employeeRepository
                .findByIdForUpdate(employeeId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Không tìm thấy nhân viên: " + employeeId));

        Map<String, String> before = snapshot(employee);

        employee.setUpdatedBy(actorAccountId);

        // Dùng lại nghiệp vụ đang có, không viết lại quy tắc nghỉ việc.
        employeeService.deleteEmployee(employeeId);

        Map<String, String> after = snapshot(employee);

        auditService.recordTermination(
                actorAccountId,
                employeeId,
                before,
                after);
    }

    private Map<String, String> snapshot(Employee employee) {
        Map<String, String> data = new LinkedHashMap<>();

        data.put(
                "employmentStatus",
                employee.getEmploymentStatus() == null
                        ? null
                        : employee.getEmploymentStatus().name());

        data.put(
                "terminationDate",
                employee.getTerminationDate() == null
                        ? null
                        : employee.getTerminationDate().toString());

        return data;
    }
}