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
@RequestMapping("/api/admin/roles")
@PreAuthorize("hasRole('ADMIN') and hasAuthority('ACCOUNT_MANAGE')")
public class AdminRoleController {
    private final AdminAccessService service;
    public AdminRoleController(AdminAccessService service) { this.service = service; }
    @GetMapping
    public List<RoleView> list() { return service.roles(); }
    @GetMapping("/{id}")
    public RoleView get(@PathVariable long id) { return service.role(id); }
    @PutMapping("/{id}/permissions")
    public RoleView permissions(@AuthenticationPrincipal AuthenticatedAccount actor, @PathVariable long id,
            @Valid @RequestBody ReplacePermissions request) { return service.replacePermissions(actor, id, request); }
}
