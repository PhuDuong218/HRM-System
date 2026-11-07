package com.hrm.service;

import com.hrm.dto.WorkTimeDTOs.ScheduleView;
import com.hrm.security.AuthenticatedAccount;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ScheduleQueryHrScopesTest {
    private final JdbcTemplate db = mock(JdbcTemplate.class);
    private final ScheduleQueryService service = new ScheduleQueryService(db);
    private final LocalDate day = LocalDate.of(2026, 10, 24);

    private void stubSearch() {
        when(db.queryForObject(anyString(), eq(Long.class), any(Object[].class))).thenReturn(0L);
        when(db.query(anyString(), org.mockito.ArgumentMatchers.<RowMapper<ScheduleView>>any(),
                any(Object[].class))).thenReturn(List.of());
    }

    @Test
    void hrSearchUsesAssignedScopeAndAccountIdOnly() {
        stubSearch();
        var hr = new AuthenticatedAccount(5L, 2L, "hr", "HR");
        service.search(hr, null, 2L, day, day, 0, false);
        var sql = ArgumentCaptor.forClass(String.class);
        var args = ArgumentCaptor.forClass(Object[].class);
        verify(db).queryForObject(sql.capture(), eq(Long.class), args.capture());
        assertTrue(sql.getValue().contains("dbo.hr_department_scopes"));
        assertTrue(sql.getValue().contains("scope.hr_account_id = ?"));
        assertTrue(sql.getValue().contains("scope.revoked_at IS NULL"));
        assertTrue(sql.getValue().contains("hr_account.is_active = 1"));
        assertTrue(sql.getValue().contains("SYSUTCDATETIME()"));
        assertFalse(sql.getValue().contains("JOIN dbo.employee_assignments hr"));
        assertArrayEquals(new Object[] { day, day, 2L, 5L }, args.getValue());
    }

    @Test
    void hrDetailChecksSameScopeUsingAccountId() {
        var hr = new AuthenticatedAccount(5L, 2L, "hr", "HR");
        when(db.query(anyString(), org.mockito.ArgumentMatchers.<RowMapper<ScheduleView>>any(),
                any(Object[].class))).thenReturn(List.of());
        assertThrows(org.springframework.web.server.ResponseStatusException.class,
                () -> service.getAuthorized(hr, 77L));
        var sql = ArgumentCaptor.forClass(String.class);
        var args = ArgumentCaptor.forClass(Object[].class);
        verify(db).query(sql.capture(), org.mockito.ArgumentMatchers.<RowMapper<ScheduleView>>any(), args.capture());
        assertTrue(sql.getValue().contains("dbo.hr_department_scopes"));
        assertArrayEquals(new Object[] { 77L, 5L }, args.getValue());
    }

    @Test
    void departmentUsesExclusiveEndDateAndShiftStartDate() {
        stubSearch();
        var admin = new AuthenticatedAccount(2L, 1L, "admin", "ADMIN");
        service.search(admin, null, 2L, day, day, 0, false);
        var sql = ArgumentCaptor.forClass(String.class);
        verify(db).queryForObject(sql.capture(), eq(Long.class), any(Object[].class));
        assertTrue(sql.getValue().contains("ea.effective_to>CAST(s.planned_start AS DATE)"));
        assertFalse(sql.getValue().contains("ea.effective_to>=s.work_date"));
    }

    @Test
    void personalQueryDoesNotUseHrDepartmentScopes() {
        stubSearch();
        var emp = new AuthenticatedAccount(9L, 3L, "emp", "EMPLOYEE");
        service.search(emp, null, null, day, day, 0, true);
        var sql = ArgumentCaptor.forClass(String.class);
        var args = ArgumentCaptor.forClass(Object[].class);
        verify(db).queryForObject(sql.capture(), eq(Long.class), args.capture());
        assertFalse(sql.getValue().contains("hr_department_scopes"));
        assertArrayEquals(new Object[] { day, day, 3L }, args.getValue());
    }
}
