package com.hrm.controller;

import com.hrm.dto.AdminAccessDTOs.*;
import com.hrm.service.AdminAccessService;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
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
}
