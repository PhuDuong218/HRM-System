package com.hrm.controller;

import com.hrm.dto.HrScopeDTOs.*;
import com.hrm.security.AuthenticatedAccount;
import com.hrm.service.HrDepartmentScopeService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/hr-scopes")
@PreAuthorize("hasRole('ADMIN') and hasAuthority('ACCOUNT_MANAGE')")
public class HrDepartmentScopeController {
    private final HrDepartmentScopeService service;

    public HrDepartmentScopeController(HrDepartmentScopeService service) {
        this.service = service;
    }

    @GetMapping
    public List<ScopeView> all(@AuthenticationPrincipal AuthenticatedAccount actor) {
        return service.all(actor);
    }

    @GetMapping("/account/{accountId}/active-departments")
    @PreAuthorize("hasRole('ADMIN') and hasAuthority('ACCOUNT_MANAGE')")
    public List<ActiveDepartment> activeDepartments(@AuthenticationPrincipal AuthenticatedAccount actor,
            @PathVariable long accountId) {
        return service.activeDepartments(actor, accountId);
    }

    @PostMapping
    public ScopeView grant(@AuthenticationPrincipal AuthenticatedAccount actor,
            @Valid @RequestBody GrantRequest request) {
        return service.grant(actor, request);
    }

    @GetMapping("/account/{accountId}/department-options")
    @PreAuthorize("hasRole('ADMIN') and hasAuthority('ACCOUNT_MANAGE')")
    public List<DepartmentOption> departmentOptions(@AuthenticationPrincipal AuthenticatedAccount actor,
            @PathVariable long accountId) {
        return service.departmentOptions(actor, accountId);
    }

    @PutMapping("/account/{accountId}/departments")
    @PreAuthorize("hasRole('ADMIN') and hasAuthority('ACCOUNT_MANAGE')")
    public List<ActiveDepartment> replaceDepartments(@AuthenticationPrincipal AuthenticatedAccount actor,
            @PathVariable long accountId, @Valid @RequestBody ReplaceDepartmentsRequest request) {
        return service.replaceDepartments(actor, accountId, request);
    }

    @PutMapping("/{id}")
    public ScopeView update(@AuthenticationPrincipal AuthenticatedAccount actor,
            @PathVariable long id, @Valid @RequestBody UpdateRequest request) {
        return service.update(actor, id, request);
    }

    @PostMapping("/{id}/revoke")
    public ScopeView revoke(@AuthenticationPrincipal AuthenticatedAccount actor,
            @PathVariable long id, @Valid @RequestBody RevokeRequest request) {
        return service.revoke(actor, id, request);
    }
}
