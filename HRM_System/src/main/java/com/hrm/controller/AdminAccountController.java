package com.hrm.controller;

import com.hrm.dto.ResetPasswordRequest;
import com.hrm.security.AuthenticatedAccount;
import com.hrm.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/accounts")
public class AdminAccountController {

    private final AuthService authService;

    public AdminAccountController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/{accountId}/reset-password")
    public ResponseEntity<Void> resetPassword(
            @AuthenticationPrincipal AuthenticatedAccount account,
            @PathVariable Long accountId,
            @Valid @RequestBody ResetPasswordRequest request) {
        authService.resetPassword(account.accountId(), accountId, request);
        return ResponseEntity.noContent().build();
    }
}
