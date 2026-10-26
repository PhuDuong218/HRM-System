package com.hrm.controller;

import java.security.Principal;
import com.hrm.service.EmployeeCreationAuditService;
import com.hrm.dto.EmployeeDTO;
import com.hrm.dto.EmployeeRequestDTO;
import com.hrm.dto.EmployeeUpdateRequestDTO;
import com.hrm.enums.EmploymentStatus;

import com.hrm.service.DepartmentHeadAccessService;
import com.hrm.service.EmployeeService;
import com.hrm.service.EmployeeTerminationAuditService;

import jakarta.validation.Valid;

import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/employees")
public class EmployeeController {

    private final EmployeeService employeeService;
    private final DepartmentHeadAccessService accessService;
    private final EmployeeTerminationAuditService terminationAudit;

    private final EmployeeCreationAuditService creationAudit;
   public EmployeeController(
        EmployeeService employeeService,
        DepartmentHeadAccessService accessService,
        EmployeeTerminationAuditService terminationAudit,
        EmployeeCreationAuditService creationAudit) {

    this.employeeService = employeeService;
    this.accessService = accessService;
    this.terminationAudit = terminationAudit;
    this.creationAudit = creationAudit;
}

    @GetMapping
    public Page<EmployeeDTO> getAllEmployees(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            Principal principal) {

        accessService.requireManager(principal);

        return employeeService.getEmployeesPage(page, size);
    }

    @GetMapping("/{id}")
    public EmployeeDTO getEmployee(
            @PathVariable Long id,
            Principal principal) {

        accessService.requireManager(principal);

        return employeeService.getEmployeeById(id);
    }

    @PostMapping
@ResponseStatus(HttpStatus.CREATED)
public EmployeeDTO addEmployee(
        @Valid @RequestBody EmployeeRequestDTO requestDTO,
        Principal principal) {

    Long actorAccountId = accessService.requireManager(principal);

    return creationAudit.create(requestDTO, actorAccountId);
}

    @PutMapping("/{id}")
    public EmployeeDTO updateEmployee(
            @PathVariable Long id,
            @Valid @RequestBody EmployeeUpdateRequestDTO requestDTO,
            Principal principal) {

        Long actorAccountId = accessService.requireManager(principal);

        return creationAudit.update(id, requestDTO, actorAccountId);
    }

    @DeleteMapping(
            value = "/{id}",
            produces = "text/plain;charset=UTF-8")
    public String deleteEmployee(
            @PathVariable Long id,
            Principal principal) {

        Long actorAccountId =
                accessService.requireManager(principal);

        terminationAudit.terminate(id, actorAccountId);

        return "Đã xử lý nghỉ việc cho nhân viên có ID: " + id;
    }

    @GetMapping("/search")
    public Page<EmployeeDTO> searchEmployees(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) EmploymentStatus status,
            @RequestParam(required = false) Long departmentId,
            @RequestParam(required = false) Long positionId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            Principal principal) {

        accessService.requireManager(principal);

        return employeeService.searchEmployees(
                keyword,
                status,
                departmentId,
                positionId,
                page,
                size);
    }
}
