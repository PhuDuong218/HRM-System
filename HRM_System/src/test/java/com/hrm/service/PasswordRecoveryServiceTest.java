package com.hrm.service;

import com.hrm.enums.EmploymentStatus;
import com.hrm.model.Account;
import com.hrm.model.Employee;
import com.hrm.model.PasswordRecovery;
import com.hrm.repository.*;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.crypto.password.PasswordEncoder;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class PasswordRecoveryServiceTest {
    AccountRepository accounts = mock(AccountRepository.class);
    EmployeeRepository employees = mock(EmployeeRepository.class);
    PasswordRecoveryRepository recoveries = mock(PasswordRecoveryRepository.class);
    AuditLogRepository audit = mock(AuditLogRepository.class);
    PasswordEncoder encoder = mock(PasswordEncoder.class);
    @SuppressWarnings("unchecked") ObjectProvider<JavaMailSender> provider = mock(ObjectProvider.class);
    JavaMailSender mail = mock(JavaMailSender.class);
    Account account = mock(Account.class);
    Employee employee = mock(Employee.class);
    PasswordRecovery saved;
    String code;
    PasswordRecoveryService service;

    @BeforeEach void setup() {
        when(provider.getIfAvailable()).thenReturn(mail);
        when(accounts.findByUsernameForRecovery("user")).thenReturn(Optional.of(account));
        when(account.getAccountId()).thenReturn(1L);
        when(account.getEmployeeId()).thenReturn(2L);
        when(account.isActive()).thenReturn(true);
        when(employees.existsByEmployeeIdAndEmploymentStatus(2L, EmploymentStatus.DANG_LAM)).thenReturn(true);
        when(employees.findById(2L)).thenReturn(Optional.of(employee));
        when(employee.getEmail()).thenReturn("employee@example.com");
        when(recoveries.findById(1L)).thenAnswer(invocation -> Optional.ofNullable(saved));
        when(recoveries.save(any(PasswordRecovery.class))).thenAnswer(invocation -> saved = invocation.getArgument(0));
        doAnswer(invocation -> { saved = null; return null; }).when(recoveries).delete(any(PasswordRecovery.class));
        doAnswer(invocation -> {
            SimpleMailMessage message = invocation.getArgument(0);
            code = message.getText().split("là: ")[1].substring(0, 8);
            return null;
        }).when(mail).send(any(SimpleMailMessage.class));
        when(encoder.encode(anyString())).thenReturn("encoded");
        when(accounts.updatePasswordAndIncrementTokenVersion(1L, "encoded")).thenReturn(1);
        service = new PasswordRecoveryService(accounts, employees, recoveries, audit, encoder, provider, "test-secret", "hrm@example.com");
    }

    @Test void codeCanBeUsedOnceAndRevokesSessions() {
        service.request("user");
        assertNotEquals(code, saved.codeHash);
        assertTrue(service.verify("user", code));
        assertTrue(service.reset("user", code, "password123", "password123"));
        assertFalse(service.reset("user", code, "password123", "password123"));
        verify(accounts).updatePasswordAndIncrementTokenVersion(1L, "encoded");
        verify(audit).save(any());
    }

    @Test void wrongCodesExpireAndRequestsAreThrottled() {
        service.request("user");
        String wrong = code.equals("00000000") ? "00000001" : "00000000";
        for (int i = 0; i < 5; i++) assertFalse(service.verify("user", wrong));
        assertFalse(service.verify("user", code));
        service.request("user");
        verify(mail, times(1)).send(any(SimpleMailMessage.class));
        saved.sentAt = Instant.now().minusSeconds(61);
        service.request("user");
        assertTrue(service.verify("user", code));
        saved.expiresAt = Instant.now().minusSeconds(1);
        assertFalse(service.verify("user", code));
    }

    @Test void accountChangesAndInvalidPasswordsDoNotReset() {
        service.request("user");
        assertThrows(IllegalArgumentException.class, () -> service.reset("user", code, "short", "short"));
        when(account.getTokenVersion()).thenReturn(1);
        assertFalse(service.reset("user", code, "password123", "password123"));
        verify(accounts, never()).updatePasswordAndIncrementTokenVersion(anyLong(), anyString());
    }
}
