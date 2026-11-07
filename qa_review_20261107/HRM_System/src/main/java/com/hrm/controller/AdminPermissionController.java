package com.hrm.controller;

import com.hrm.dto.AdminAccessDTOs.*;
import com.hrm.security.AuthenticatedAccount;
import com.hrm.service.AdminAccessService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/permissions")
@PreAuthorize("hasRole('ADMIN') and hasAuthority('ACCOUNT_MANAGE')")
public class AdminPermissionController {
    private final AdminAccessService service;
    public AdminPermissionController(AdminAccessService service) { this.service = service; }
    @GetMapping
    public List<PermissionView> list() { return service.permissions(); }
    @GetMapping("/{id}")
    public PermissionView get(@PathVariable long id) { return service.permission(id); }
    @PutMapping("/{id}")
    public PermissionView update(@AuthenticationPrincipal AuthenticatedAccount actor, @PathVariable long id,
            @Valid @RequestBody UpdatePermission request) { return service.updatePermission(actor, id, request); }
    @PatchMapping("/{id}/details")
    public PermissionView details(@AuthenticationPrincipal AuthenticatedAccount actor, @PathVariable long id,
            @Valid @RequestBody EditPermissionDetails request) { return service.editPermissionDetails(actor, id, request); }
}
