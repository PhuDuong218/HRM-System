package com.hrm.controller;

import com.hrm.dto.AdminAccessDTOs.*;
import com.hrm.dto.PageResponse;
import com.hrm.security.AuthenticatedAccount;
import com.hrm.service.AdminAccessService;
import jakarta.validation.Valid;
import java.net.URI;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/accounts")
@PreAuthorize("hasRole('ADMIN') and hasAuthority('ACCOUNT_MANAGE')")
public class AdminAccountManagementController {
    private final AdminAccessService service;
    public AdminAccountManagementController(AdminAccessService service) { this.service = service; }

    @GetMapping
    public PageResponse<AccountView> list(@RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size, @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Boolean active, @RequestParam(required = false) Long roleId) {
        return service.accounts(page, size, keyword, active, roleId);
    }
    @GetMapping("/{id}")
    public AccountView get(@PathVariable long id) { return service.account(id); }

    @PostMapping
    public ResponseEntity<AccountView> create(@AuthenticationPrincipal AuthenticatedAccount actor,
            @Valid @RequestBody CreateAccount request) {
        var result = service.createAccount(actor, request);
        return ResponseEntity.created(URI.create("/api/admin/accounts/" + result.accountId())).body(result);
    }
    @PutMapping("/{id}")
    public AccountView update(@AuthenticationPrincipal AuthenticatedAccount actor, @PathVariable long id,
            @Valid @RequestBody UpdateAccount request) { return service.updateAccount(actor, id, request); }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@AuthenticationPrincipal AuthenticatedAccount actor, @PathVariable long id) {
        service.deleteAccount(actor, id);
        return ResponseEntity.noContent().build();
    }
}
