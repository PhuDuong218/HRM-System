package com.hrm.service;

import com.hrm.dto.ChangePasswordRequest;
import com.hrm.dto.LoginRequest;
import com.hrm.dto.LoginResponse;
import com.hrm.dto.ResetPasswordRequest;
import com.hrm.enums.EmploymentStatus;
import com.hrm.model.Account;
import com.hrm.repository.AuditLogRepository;
import com.hrm.repository.AccountRepository;
import com.hrm.repository.EmployeeRepository;
import com.hrm.security.JwtService;
import jakarta.transaction.Transactional;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class AuthService {

    private final AccountRepository accountRepository;
    private final EmployeeRepository employeeRepository;
    private final AuditLogRepository auditLogRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public AuthService(
            AccountRepository accountRepository,
            EmployeeRepository employeeRepository,
            AuditLogRepository auditLogRepository,
            PasswordEncoder passwordEncoder,
            JwtService jwtService) {
        this.accountRepository = accountRepository;
        this.employeeRepository = employeeRepository;
        this.auditLogRepository = auditLogRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    public LoginResponse login(LoginRequest request) {
        Account account = accountRepository.findByUsername(request.username())
                .orElseThrow(() -> new BadCredentialsException("Tên đăng nhập hoặc mật khẩu không đúng"));
        if (!account.isActive()
                || !employeeRepository.existsByEmployeeIdAndEmploymentStatus(
                        account.getEmployeeId(), EmploymentStatus.DANG_LAM)
                || !passwordEncoder.matches(request.password(), account.getPasswordHash())) {
            throw new BadCredentialsException("Tên đăng nhập hoặc mật khẩu không đúng");
        }

        String token = jwtService.issue(
                account.getAccountId(),
                account.getEmployeeId(),
                account.getUsername(),
                account.getRole().getRoleCode(),
                account.getTokenVersion());
        return new LoginResponse(token, "Bearer", jwtService.getExpirationSeconds());
    }

    @Transactional
    public void logout(Long accountId) {
        if (accountRepository.incrementTokenVersion(accountId) != 1) {
            throw new IllegalStateException("Không thể vô hiệu phiên đăng nhập");
        }
    }

    @Transactional
    public void changePassword(Long accountId, ChangePasswordRequest request) {
        Account account = getActiveAccount(accountId);
        validateNewPassword(request.newPassword(), request.confirmPassword());
        if (!passwordEncoder.matches(request.currentPassword(), account.getPasswordHash())) {
            throw new BadCredentialsException("Mật khẩu hiện tại không đúng");
        }
        if (passwordEncoder.matches(request.newPassword(), account.getPasswordHash())) {
            throw new IllegalArgumentException("Mật khẩu mới phải khác mật khẩu hiện tại");
        }

        updatePassword(accountId, accountId, request.newPassword(), "PASSWORD_CHANGED");
    }

    @Transactional
    public void resetPassword(Long actorAccountId, Long targetAccountId, ResetPasswordRequest request) {
        Account actor = getActiveAccount(actorAccountId);
        if (!"ADMIN".equals(actor.getRole().getRoleCode())) {
            throw new AccessDeniedException("Chỉ ADMIN được reset mật khẩu tài khoản khác");
        }
        if (actorAccountId.equals(targetAccountId)) {
            throw new IllegalArgumentException("Dùng chức năng đổi mật khẩu cho tài khoản của chính mình");
        }
        getActiveAccount(targetAccountId);
        validateNewPassword(request.newPassword(), request.confirmPassword());
        updatePassword(actorAccountId, targetAccountId, request.newPassword(), "PASSWORD_RESET");
    }

    private void updatePassword(Long actorAccountId, Long targetAccountId, String rawPassword, String action) {
        String passwordHash = passwordEncoder.encode(rawPassword);
        if (accountRepository.updatePasswordAndIncrementTokenVersion(targetAccountId, passwordHash) != 1) {
            throw new IllegalStateException("Không thể cập nhật mật khẩu");
        }
        auditLogRepository.save(new com.hrm.model.AuditLog(
                actorAccountId, action, "accounts", targetAccountId, "Password security operation"));
    }

    private Account getActiveAccount(Long accountId) {
        Account account = accountRepository.findById(accountId)
                .orElseThrow(() -> new IllegalArgumentException("Tài khoản không tồn tại"));
        if (!account.isActive()
                || !employeeRepository.existsByEmployeeIdAndEmploymentStatus(
                        account.getEmployeeId(), EmploymentStatus.DANG_LAM)) {
            throw new AccessDeniedException("Tài khoản đã bị khóa hoặc nhân viên không còn làm việc");
        }
        return account;
    }

    private void validateNewPassword(String newPassword, String confirmPassword) {
        if (!newPassword.equals(confirmPassword)) {
            throw new IllegalArgumentException("Mật khẩu mới và xác nhận mật khẩu không khớp");
        }
    }
}
