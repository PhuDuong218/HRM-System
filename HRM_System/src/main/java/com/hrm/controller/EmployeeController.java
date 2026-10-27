package com.hrm.controller;

import java.security.Principal;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.hrm.dto.EmployeeDTO;
import com.hrm.dto.EmployeeRequestDTO;
import com.hrm.dto.EmployeeUpdateRequestDTO;
import com.hrm.enums.EmploymentStatus;
import com.hrm.security.AuthenticatedAccount;
import com.hrm.service.DepartmentHeadAccessService;
import com.hrm.service.EmployeeCreationAuditService;
import com.hrm.service.EmployeeService;
import com.hrm.service.EmployeeTerminationAuditService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/employees")
public class EmployeeController {

    private final EmployeeService employeeService;
    private final DepartmentHeadAccessService accessService;
    private final EmployeeTerminationAuditService terminationAudit;
    private final EmployeeCreationAuditService creationAudit;

    public EmployeeController(EmployeeService employeeService, DepartmentHeadAccessService accessService,
            EmployeeTerminationAuditService terminationAudit, EmployeeCreationAuditService creationAudit) {
        this.employeeService = employeeService;
        this.accessService = accessService;
        this.terminationAudit = terminationAudit;
        this.creationAudit = creationAudit;
    }

    @GetMapping(params = {"!page", "!size"})
    @PreAuthorize("hasAnyRole('ADMIN','HR') and hasAuthority('EMPLOYEE_MANAGE')")
    public List<EmployeeDTO> listEmployees(Principal principal) {
        actorAccountId(principal);
        AuthenticatedAccount actor = authenticatedAccount(principal);
        return actor == null ? employeeService.getAllEmployees() : employeeService.getAllEmployees(actor);
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','HR') and hasAuthority('EMPLOYEE_MANAGE')")
    public Page<EmployeeDTO> getEmployeesPage(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            Principal principal) {
        actorAccountId(principal);
        return employeeService.getEmployeesPage(page, size);
    }

    @GetMapping("/{id}")
    @PreAuthorize("(hasAnyRole('ADMIN','HR') and hasAuthority('EMPLOYEE_MANAGE'))"
            + " or (hasAuthority('SELF_READ') and #id == authentication.principal.employeeId())")
    public EmployeeDTO getEmployeeDetails(@PathVariable Long id, Principal principal) {
        AuthenticatedAccount actor = authenticatedAccount(principal);
        if (actor != null) {
            return employeeService.getEmployeeById(id, actor);
        }
        actorAccountId(principal);
        return employeeService.getEmployeeById(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('ADMIN','HR') and hasAuthority('EMPLOYEE_MANAGE')")
    public EmployeeDTO createEmployee(@Valid @RequestBody EmployeeRequestDTO requestDTO, Principal principal) {
        return creationAudit.create(requestDTO, actorAccountId(principal));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','HR') and hasAuthority('EMPLOYEE_MANAGE')")
    public EmployeeDTO replaceEmployee(@PathVariable Long id,
            @Valid @RequestBody EmployeeUpdateRequestDTO requestDTO, Principal principal) {
        return creationAudit.update(id, requestDTO, actorAccountId(principal));
    }

    @DeleteMapping(value = "/{id}", produces = "text/plain;charset=UTF-8")
    @PreAuthorize("hasAnyRole('ADMIN','HR') and hasAuthority('EMPLOYEE_MANAGE')")
    public String terminateEmployee(@PathVariable Long id, Principal principal) {
        terminationAudit.terminateIfActive(id, actorAccountId(principal));
        return "Đã xử lý nghỉ việc cho nhân viên có ID: " + id;
    }

    @GetMapping("/search")
    @PreAuthorize("hasAnyRole('ADMIN','HR') and hasAuthority('EMPLOYEE_MANAGE')")
    public Page<EmployeeDTO> searchEmployees(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) EmploymentStatus status,
            @RequestParam(required = false) Long departmentId,
            @RequestParam(required = false) Long positionId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            Principal principal) {
        actorAccountId(principal);
        return employeeService.searchEmployees(keyword, status, departmentId, positionId, page, size);
    }

    @PreAuthorize("hasAnyRole('ADMIN','HR') and hasAuthority('EMPLOYEE_MANAGE')")
    public List<EmployeeDTO> getAllEmployees(Authentication authentication) {
        return employeeService.getAllEmployees((AuthenticatedAccount) authentication.getPrincipal());
    }

    @PreAuthorize("(hasAnyRole('ADMIN','HR') and hasAuthority('EMPLOYEE_MANAGE'))"
            + " or (hasAuthority('SELF_READ') and #id == authentication.principal.employeeId())")
    public EmployeeDTO getEmployee(Long id, Authentication authentication) {
        return employeeService.getEmployeeById(id, (AuthenticatedAccount) authentication.getPrincipal());
    }

    @PreAuthorize("hasAnyRole('ADMIN','HR') and hasAuthority('EMPLOYEE_MANAGE')")
    public EmployeeDTO addEmployee(EmployeeRequestDTO requestDTO) {
        return creationAudit.create(requestDTO, currentActorAccountId());
    }

    @PreAuthorize("hasAnyRole('ADMIN','HR') and hasAuthority('EMPLOYEE_MANAGE')")
    public EmployeeDTO updateEmployee(Long id, EmployeeRequestDTO requestDTO) {
        return creationAudit.update(id, toUpdateRequest(requestDTO), currentActorAccountId());
    }

    @PreAuthorize("hasAnyRole('ADMIN','HR') and hasAuthority('EMPLOYEE_MANAGE')")
    public String deleteEmployee(Long id) {
        employeeService.deleteEmployee(id);
        return "Đã xóa nhân viên có mã: " + id;
    }

    private EmployeeUpdateRequestDTO toUpdateRequest(EmployeeRequestDTO request) {
        if (request == null) {
            return null;
        }
        EmployeeUpdateRequestDTO update = new EmployeeUpdateRequestDTO();
        update.setFullName(request.getFullName());
        update.setBirthDate(request.getBirthDate());
        update.setEmail(request.getEmail());
        update.setPhone(request.getPhone());
        update.setIdentityNumber(request.getIdentityNumber());
        return update;
    }

    private Long currentActorAccountId() {
        return actorAccountId(SecurityContextHolder.getContext().getAuthentication());
    }

    private Long actorAccountId(Principal principal) {
        AuthenticatedAccount actor = authenticatedAccount(principal);
        return actor == null ? accessService.requireManager(principal) : actor.accountId();
    }

    private AuthenticatedAccount authenticatedAccount(Principal principal) {
        if (principal instanceof Authentication authentication
                && authentication.isAuthenticated()
                && authentication.getPrincipal() instanceof AuthenticatedAccount actor) {
            return actor;
        }
        return null;
    }
}
