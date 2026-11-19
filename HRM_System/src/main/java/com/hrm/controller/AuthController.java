package com.hrm.controller;

import com.hrm.dto.ChangePasswordRequest;
import com.hrm.dto.CurrentUserResponse;
import com.hrm.dto.LoginRequest;
import com.hrm.dto.LoginResponse;
import com.hrm.security.AuthenticatedAccount;
import com.hrm.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        return ResponseEntity.ok(authService.login(request));
    }

    @PostMapping("/web/login")
    public ResponseEntity<LoginResponse> loginWeb(@Valid @RequestBody LoginRequest request) {
        return ResponseEntity.ok(authService.loginWeb(request));
    }

    @PostMapping("/web/refresh")
    public ResponseEntity<LoginResponse> refreshWeb(@AuthenticationPrincipal AuthenticatedAccount account,
                                                    @RequestHeader(value = "Authorization", required = false) String authorization) {
        if (authorization == null || !authorization.startsWith("Bearer ") || authorization.length() <= 7) {
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.UNAUTHORIZED, "Thiếu token xác thực hợp lệ.");
        }
        return ResponseEntity.ok(authService.refreshWeb(account, authorization.substring(7)));
    }

    @GetMapping("/me")
    public CurrentUserResponse currentUser(@AuthenticationPrincipal AuthenticatedAccount account) {
        return authService.currentUser(account);
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@AuthenticationPrincipal AuthenticatedAccount account) {
        authService.logout(account.accountId());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/change-password")
    public ResponseEntity<Void> changePassword(
            @AuthenticationPrincipal AuthenticatedAccount account,
            @Valid @RequestBody ChangePasswordRequest request) {
        authService.changePassword(account.accountId(), request);
        return ResponseEntity.noContent().build();
    }

}
