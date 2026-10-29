package com.hrm.security;

import com.hrm.dto.ChangePasswordRequest;
import com.hrm.enums.EmploymentStatus;
import com.hrm.model.Account;
import com.hrm.model.AuditLog;
import com.hrm.repository.AccountRepository;
import com.hrm.repository.AuditLogRepository;
import com.hrm.repository.EmployeeAssignmentRepository;
import com.hrm.repository.EmployeeRepository;
import com.hrm.service.AuthService;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ChangePasswordServiceTest {
    private final AccountRepository accounts = mock(AccountRepository.class);
    private final EmployeeRepository employees = mock(EmployeeRepository.class);
    private final EmployeeAssignmentRepository assignments = mock(EmployeeAssignmentRepository.class);
    private final AuditLogRepository audit = mock(AuditLogRepository.class);
    private final PasswordEncoder encoder = mock(PasswordEncoder.class);
    private final JwtService jwt = mock(JwtService.class);
    private final AuthService service = new AuthService(accounts, employees, assignments, audit, encoder, jwt);

    private void activeAccount() {
        Account account = mock(Account.class);
        when(account.isActive()).thenReturn(true);
        when(account.getEmployeeId()).thenReturn(2L);
        when(account.getPasswordHash()).thenReturn("existing-hash");
        when(accounts.findById(1L)).thenReturn(Optional.of(account));
        when(employees.existsByEmployeeIdAndEmploymentStatus(2L, EmploymentStatus.DANG_LAM)).thenReturn(true);
    }

    @Test
    void incorrectCurrentPasswordIsAValidationErrorAndChangesNothing() {
        activeAccount();
        var ex = assertThrows(IllegalArgumentException.class, () -> service.changePassword(1L,
                new ChangePasswordRequest("wrong-password", "new-password", "new-password")));
        assertEquals("Mật khẩu hiện tại không đúng", ex.getMessage());
        verify(accounts, never()).updatePasswordAndIncrementTokenVersion(anyLong(), anyString());
        verifyNoInteractions(audit);
    }

    @Test
    void successfulChangeStoresHashAndRevokesOldTokens() {
        activeAccount();
        when(encoder.matches("old-password", "existing-hash")).thenReturn(true);
        when(encoder.matches("new-password", "existing-hash")).thenReturn(false);
        when(encoder.encode("new-password")).thenReturn("new-hash");
        when(accounts.updatePasswordAndIncrementTokenVersion(1L, "new-hash")).thenReturn(1);
        service.changePassword(1L, new ChangePasswordRequest("old-password", "new-password", "new-password"));
        verify(accounts).updatePasswordAndIncrementTokenVersion(1L, "new-hash");
        verify(audit).save(any(AuditLog.class));
    }
}
