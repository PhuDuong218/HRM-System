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
    @org.springframework.beans.factory.annotation.Autowired private EmployeeSupplement supplement;

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

        if(supplement!=null){supplement.save(result.getEmployeeId(),request.getGender(),actorAccountId,"Tạo/cập nhật thông tin hồ sơ");result.setGender(supplement.get(result.getEmployeeId()));}
        result.setVersion(EmployeeWriteRules.version(employee));
        return result;
    }

    /** Legacy PUT keeps an omitted address; profile/contact changes share the audit snapshot. */
    @Transactional
    public EmployeeDTO update(Long id, EmployeeUpdateRequestDTO request, Long actorAccountId) {
        if (actorAccountId == null || actorAccountId <= 0) {
            throw new IllegalArgumentException("Phải xác định tài khoản cập nhật nhân viên");
        }
        Employee employee = employeeRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy nhân viên: " + id));
        EmployeeWriteRules.requireVersion(employee,request.getVersion());
        request.setReason(EmployeeWriteRules.reason(request.getReason()));
        EmployeeWriteRules.requiredIfPresent(request.hasEmail(),request.getEmail(),"Email");
        EmployeeWriteRules.requiredIfPresent(request.hasAddress(),request.getAddress(),"Địa chỉ");
        EmployeeWriteRules.requiredIfPresent(request.hasIdentityNumber(),request.getIdentityNumber(),"CCCD/CMND");
        EmployeeWriteRules.identity(request.getIdentityNumber());
        Map<String, String> before = snapshot(employee);
        employee.setUpdatedBy(actorAccountId);
        EmployeeDTO result = employeeService.updateEmployee(id, request);
        employeeRepository.flush();
        if(supplement!=null){supplement.saveProfile(id,request.getGender(),actorAccountId,request.getReason());result.setGender(supplement.get(id));}
        auditService.recordProfileUpdate(actorAccountId, id, before, snapshot(employee),request.getReason());
        result.setVersion(EmployeeWriteRules.version(employee));
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
        if(supplement!=null)data.put("gender",supplement.get(employee.getEmployeeId()));

        return data;
    }

    private String text(Object value) {
        return value == null ? null : value.toString();
    }
}
