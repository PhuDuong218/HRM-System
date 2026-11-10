package com.hrm.service;

import com.hrm.enums.EmploymentStatus;
import com.hrm.model.Account;
import com.hrm.model.Employee;
import com.hrm.model.Role;
import com.hrm.repository.AccountRepository;
import com.hrm.repository.AuditLogRepository;
import com.hrm.repository.EmployeeRepository;
import com.hrm.repository.PermissionRepository;
import com.hrm.repository.RoleRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AdminAccountEmploymentStatusTest {
    @Test
    void listAndDetailShowEmployeeStatusIndependentlyOfAccountStatus() {
        var accounts = mock(AccountRepository.class);
        var employees = mock(EmployeeRepository.class);
        var service = new AdminAccessService(accounts, employees, mock(RoleRepository.class),
                mock(PermissionRepository.class), mock(AuditLogRepository.class), mock(PasswordEncoder.class),
                mock(HrDepartmentScopeService.class));
        var account = mock(Account.class);
        var employee = mock(Employee.class);
        when(account.getEmployeeId()).thenReturn(12L);
        when(account.getRole()).thenReturn(mock(Role.class));
        when(account.isActive()).thenReturn(true);
        when(employee.getEmployeeId()).thenReturn(12L);
        when(employee.getEmploymentStatus()).thenReturn(EmploymentStatus.DA_NGHI);
        when(accounts.findById(5L)).thenReturn(Optional.of(account));
        when(employees.findById(12L)).thenReturn(Optional.of(employee));
        when(accounts.search(anyString(), isNull(), isNull(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(account)));
        when(employees.findAllById(any())).thenReturn(List.of(employee));

        var detail = service.account(5L);
        var listed = service.accounts(0, 10, null, null, null).items().get(0);
        assertTrue(detail.active());
        assertEquals(EmploymentStatus.DA_NGHI, detail.employmentStatus());
        assertEquals(EmploymentStatus.DA_NGHI, listed.employmentStatus());
        when(employee.getEmploymentStatus()).thenReturn(EmploymentStatus.DANG_LAM);
        assertEquals(EmploymentStatus.DANG_LAM, service.account(5L).employmentStatus());
        when(employees.findById(12L)).thenReturn(Optional.empty());
        assertNull(service.account(5L).employmentStatus());
    }
    @Test void departmentHeadFilterPreservesBaseRoleAndDatabasePagination(){
        var accounts=mock(AccountRepository.class);var employees=mock(EmployeeRepository.class);var db=mock(org.springframework.jdbc.core.JdbcTemplate.class);
        var service=new AdminAccessService(accounts,employees,mock(RoleRepository.class),mock(PermissionRepository.class),mock(AuditLogRepository.class),mock(PasswordEncoder.class),mock(HrDepartmentScopeService.class));
        org.springframework.test.util.ReflectionTestUtils.setField(service,"headDb",db);
        var account=mock(Account.class);var role=mock(Role.class);var employee=mock(Employee.class);
        when(account.getEmployeeId()).thenReturn(12L);when(account.getRole()).thenReturn(role);when(role.getRoleCode()).thenReturn("EMPLOYEE");when(employee.getEmployeeId()).thenReturn(12L);
        when(accounts.searchDepartmentHeads(eq("%head%"),eq(true),any(java.time.LocalDate.class),any(Pageable.class))).thenReturn(new PageImpl<>(List.of(account),org.springframework.data.domain.PageRequest.of(0,10),21));
        when(employees.findAllById(any())).thenReturn(List.of(employee));when(db.queryForList(anyString(),eq(Long.class))).thenReturn(List.of(12L));
        var page=service.accounts(0,10,"head",true,null,true);
        assertTrue(page.items().get(0).departmentHead());assertEquals("EMPLOYEE",page.items().get(0).roleCode());assertEquals(21,page.totalElements());
        verify(accounts,never()).search(anyString(),any(),any(),any());
    }
}
