package com.hrm.controller;

import com.hrm.dto.AdminAccessDTOs.*;
import com.hrm.security.AuthenticatedAccount;
import com.hrm.service.AdminAccessService;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import org.springframework.http.ResponseEntity;
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
    @PostMapping
    public ResponseEntity<RoleView> create(@AuthenticationPrincipal AuthenticatedAccount actor,
            @Valid @RequestBody CreateRole request) {
        var result = service.createRole(actor, request);
        return ResponseEntity.created(URI.create("/api/admin/roles/" + result.roleId())).body(result);
    }
    @PutMapping("/{id}")
    public RoleView update(@AuthenticationPrincipal AuthenticatedAccount actor, @PathVariable long id,
            @Valid @RequestBody UpdateRole request) { return service.updateRole(actor, id, request); }
    @PutMapping("/{id}/permissions")
    public RoleView permissions(@AuthenticationPrincipal AuthenticatedAccount actor, @PathVariable long id,
            @Valid @RequestBody ReplacePermissions request) { return service.replacePermissions(actor, id, request); }
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@AuthenticationPrincipal AuthenticatedAccount actor, @PathVariable long id) {
        service.deleteRole(actor, id);
        return ResponseEntity.noContent().build();
    }
}
