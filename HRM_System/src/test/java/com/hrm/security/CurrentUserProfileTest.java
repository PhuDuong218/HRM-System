package com.hrm.security;

import com.hrm.model.Employee;
import com.hrm.repository.AccountRepository;
import com.hrm.repository.AuditLogRepository;
import com.hrm.repository.EmployeeAssignmentRepository;
import com.hrm.repository.EmployeeRepository;
import com.hrm.service.AuthService;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CurrentUserProfileTest {
    @Test
    void currentUserIdentityComesFromAuthenticatedEmployeesOwnRecord() {
        var employees = mock(EmployeeRepository.class);
        var assignments = mock(EmployeeAssignmentRepository.class);
        var service = new AuthService(mock(AccountRepository.class), employees, assignments,
                mock(AuditLogRepository.class), mock(PasswordEncoder.class), mock(JwtService.class));
        var employee = new Employee();
        employee.setEmployeeId(42L);
        employee.setFullName("Profile owner");
        employee.setIdentityNumber("012345678901");
        when(employees.findById(42L)).thenReturn(Optional.of(employee));
        when(assignments.findCurrentAssignments(42L)).thenReturn(List.of());

        var profile = service.currentUser(new AuthenticatedAccount(7L, 42L, "owner", "HR"));

        assertEquals("012345678901", profile.identityNumber());
        verify(employees).findById(42L);
        employee.setIdentityNumber(null);
        assertNull(service.currentUser(new AuthenticatedAccount(7L, 42L, "owner", "HR")).identityNumber());
    }
}
