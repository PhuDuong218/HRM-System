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
@RequestMapping("/api/admin/permissions")
@PreAuthorize("hasRole('ADMIN') and hasAuthority('ACCOUNT_MANAGE')")
public class AdminPermissionController {
    private final AdminAccessService service;
    public AdminPermissionController(AdminAccessService service) { this.service = service; }
    @GetMapping
    public List<PermissionView> list() { return service.permissions(); }
    @GetMapping("/{id}")
    public PermissionView get(@PathVariable long id) { return service.permission(id); }
    @PostMapping
    public ResponseEntity<PermissionView> create(@AuthenticationPrincipal AuthenticatedAccount actor,
            @Valid @RequestBody CreatePermission request) {
        var result = service.createPermission(actor, request);
        return ResponseEntity.created(URI.create("/api/admin/permissions/" + result.permissionId())).body(result);
    }
    @PutMapping("/{id}")
    public PermissionView update(@AuthenticationPrincipal AuthenticatedAccount actor, @PathVariable long id,
            @Valid @RequestBody UpdatePermission request) { return service.updatePermission(actor, id, request); }
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@AuthenticationPrincipal AuthenticatedAccount actor, @PathVariable long id) {
        service.deletePermission(actor, id);
        return ResponseEntity.noContent().build();
    }
}
