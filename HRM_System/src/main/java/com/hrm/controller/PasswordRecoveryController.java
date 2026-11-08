package com.hrm.controller;

import com.hrm.service.PasswordRecoveryService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class PasswordRecoveryController {
    private final PasswordRecoveryService service;
    public PasswordRecoveryController(PasswordRecoveryService service) { this.service = service; }
    public record Request(@NotBlank @Size(max = 100) String username) {}
    public record Code(@NotBlank @Size(max = 100) String username, @NotBlank @Pattern(regexp = "[0-9]{8}") String code) {}
    public record Reset(@NotBlank @Size(max = 100) String username, @NotBlank @Pattern(regexp = "[0-9]{8}") String code,
                        @NotBlank @Size(min = 8, max = 72) String newPassword, @NotBlank @Size(max = 72) String confirmPassword) {}

    @PostMapping("/forgot-password")
    public ResponseEntity<Void> request(@Valid @RequestBody Request request) {
        service.request(request.username().trim()); return ResponseEntity.noContent().build();
    }
    @PostMapping("/verify-recovery-code")
    public ResponseEntity<Void> verify(@Valid @RequestBody Code request) {
        return service.verify(request.username().trim(), request.code()) ? ResponseEntity.noContent().build() : ResponseEntity.badRequest().build();
    }
    @PostMapping("/reset-forgotten-password")
    public ResponseEntity<Void> reset(@Valid @RequestBody Reset request) {
        return service.reset(request.username().trim(), request.code(), request.newPassword(), request.confirmPassword())
                ? ResponseEntity.noContent().build() : ResponseEntity.badRequest().build();
    }
}
