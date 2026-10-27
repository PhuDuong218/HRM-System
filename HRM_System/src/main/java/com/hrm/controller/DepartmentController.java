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

import com.hrm.dto.DepartmentDTO;
import com.hrm.dto.DepartmentRequestDTO;
import com.hrm.security.AuthenticatedAccount;
import com.hrm.service.CatalogAuditService;
import com.hrm.service.DepartmentHeadAccessService;
import com.hrm.service.DepartmentService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/departments")
public class DepartmentController {

    private final DepartmentService departmentService;
    private final DepartmentHeadAccessService access;
    private final CatalogAuditService catalog;

    public DepartmentController(DepartmentService departmentService, DepartmentHeadAccessService access,
            CatalogAuditService catalog) {
        this.departmentService = departmentService;
        this.access = access;
        this.catalog = catalog;
    }

    @GetMapping(params = {"!page", "!size", "!keyword", "!active"})
    @PreAuthorize("hasAnyRole('ADMIN','HR') and hasAuthority('DEPARTMENT_MANAGE')")
    public List<DepartmentDTO> listDepartments(Principal principal) {
        actorAccountId(principal);
        return departmentService.getAllDepartment();
    }

    @GetMapping({"", "/search"})
    @PreAuthorize("hasAnyRole('ADMIN','HR') and hasAuthority('DEPARTMENT_MANAGE')")
    public Page<DepartmentDTO> searchDepartments(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Boolean active,
            Principal principal) {
        actorAccountId(principal);
        return departmentService.searchDepartments(keyword, active, page, size);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','HR') and hasAuthority('DEPARTMENT_MANAGE')")
    public DepartmentDTO getDepartmentDetails(@PathVariable Long id, Principal principal) {
        actorAccountId(principal);
        return departmentService.getDepartment(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('ADMIN','HR') and hasAuthority('DEPARTMENT_MANAGE')")
    public DepartmentDTO createDepartment(@Valid @RequestBody DepartmentRequestDTO requestDTO,
            Principal principal) {
        return catalog.createDepartment(requestDTO, actorAccountId(principal));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','HR') and hasAuthority('DEPARTMENT_MANAGE')")
    public DepartmentDTO replaceDepartment(@PathVariable Long id,
            @Valid @RequestBody DepartmentRequestDTO requestDTO, Principal principal) {
        return catalog.updateDepartment(id, requestDTO, actorAccountId(principal));
    }

    @DeleteMapping(value = "/{id}", produces = "text/plain;charset=UTF-8")
    @PreAuthorize("hasAnyRole('ADMIN','HR') and hasAuthority('DEPARTMENT_MANAGE')")
    public String deactivateDepartment(@PathVariable Long id, Principal principal) {
        catalog.deactivateDepartment(id, actorAccountId(principal));
        return "Đã ngừng sử dụng phòng ban có mã: " + id;
    }

    // Preserve the personal branch's public controller API for existing callers.
    @PreAuthorize("hasAnyRole('ADMIN','HR') and hasAuthority('DEPARTMENT_MANAGE')")
    public List<DepartmentDTO> getAllDepartments() {
        return departmentService.getAllDepartment();
    }

    @PreAuthorize("hasAnyRole('ADMIN','HR') and hasAuthority('DEPARTMENT_MANAGE')")
    public DepartmentDTO getDepartment(Long id) {
        return departmentService.getDepartment(id);
    }

    @PreAuthorize("hasAnyRole('ADMIN','HR') and hasAuthority('DEPARTMENT_MANAGE')")
    public DepartmentDTO addDepartment(DepartmentRequestDTO requestDTO) {
        return catalog.createDepartment(requestDTO, currentActorAccountId());
    }

    @PreAuthorize("hasAnyRole('ADMIN','HR') and hasAuthority('DEPARTMENT_MANAGE')")
    public DepartmentDTO updateDepartment(Long id, DepartmentRequestDTO requestDTO) {
        return catalog.updateDepartment(id, requestDTO, currentActorAccountId());
    }

    @PreAuthorize("hasAnyRole('ADMIN','HR') and hasAuthority('DEPARTMENT_MANAGE')")
    public String deleteDepartment(Long id) {
        return deactivateDepartment(id, SecurityContextHolder.getContext().getAuthentication());
    }

    private Long currentActorAccountId() {
        return actorAccountId(SecurityContextHolder.getContext().getAuthentication());
    }

    private Long actorAccountId(Principal principal) {
        if (principal instanceof Authentication authentication
                && authentication.isAuthenticated()
                && authentication.getPrincipal() instanceof AuthenticatedAccount actor) {
            // JWT authentication already checked the active account, employee and SQL permissions.
            return actor.accountId();
        }
        return access.requireManager(principal);
    }
}
