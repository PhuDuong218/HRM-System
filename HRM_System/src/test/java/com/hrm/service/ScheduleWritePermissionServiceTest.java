package com.hrm.service;

import com.hrm.security.AuthenticatedAccount;
import java.sql.ResultSet;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.web.server.ResponseStatusException;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ScheduleWritePermissionServiceTest {
    private final JdbcTemplate db = mock(JdbcTemplate.class);
    private final ScheduleWritePermissionService service = new ScheduleWritePermissionService(db);
    private final LocalDateTime future = LocalDateTime.of(2030, 5, 1, 8, 0);

    @Test
    void anonymousIsDeniedWithoutDatabaseAccess() {
        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> service.requireCreate(null, 10L, future));
        assertEquals(HttpStatus.FORBIDDEN, error.getStatusCode());
        verifyNoInteractions(db);
    }

    @Test
    void actorWithUnknownAccountIsDenied() {
        when(db.query(anyString(), any(RowMapper.class), eq(7L))).thenReturn(List.of());
        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> service.requireCreate(new AuthenticatedAccount(7L, 20L, "user", "HR"), 10L, future));
        assertEquals(HttpStatus.FORBIDDEN, error.getStatusCode());
        verify(db, never()).queryForObject(anyString(), eq(Long.class), any(Object[].class));
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    private void mockCurrentActor(String role, long accountId, long employeeId) throws Exception {
        when(db.query(anyString(), any(RowMapper.class), eq(accountId))).thenAnswer(call -> {
            RowMapper mapper = call.getArgument(1);
            ResultSet rs = mock(ResultSet.class);
            when(rs.getString(1)).thenReturn(role);
            when(rs.getObject(2, Long.class)).thenReturn(employeeId);
            return List.of(mapper.mapRow(rs, 0));
        });
    }

    @Test
    void adminMayWriteForAnyRoleIfEmployeeIsWorking() throws Exception {
        mockCurrentActor("ADMIN", 1L, 22L);
        when(db.queryForObject(contains("dbo.employees"), eq(Long.class), eq(15L), any()))
                .thenReturn(1L);
        assertDoesNotThrow(() -> service.requireCreate(
                new AuthenticatedAccount(1L, 22L, "admin", "ADMIN"), 15L, future));
        verify(db, never()).queryForObject(contains("dbo.hr_department_scopes"), eq(Long.class), any(Object[].class));
    }

    @Test
    void hrOutsideAssignedScopeIsDenied() throws Exception {
        mockCurrentActor("HR", 2L, 23L);
        when(db.queryForObject(contains("dbo.employees"), eq(Long.class), eq(15L), any()))
                .thenReturn(1L);
        when(db.queryForObject(contains("dbo.accounts a WITH (HOLDLOCK)"), eq(Long.class), eq(15L)))
                .thenReturn(1L);
        when(db.queryForObject(contains("dbo.hr_department_scopes"), eq(Long.class), any(Object[].class)))
                .thenReturn(0L);
        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> service.requireCreate(new AuthenticatedAccount(2L, 23L, "hr", "HR"), 15L, future));
        assertEquals(HttpStatus.FORBIDDEN, error.getStatusCode());
    }

    @Test
    void hrWithAssignedScopeMayWrite() throws Exception {
        mockCurrentActor("HR", 2L, 23L);
        when(db.queryForObject(contains("dbo.employees"), eq(Long.class), eq(15L), any()))
                .thenReturn(1L);
        when(db.queryForObject(contains("dbo.accounts a WITH (HOLDLOCK)"), eq(Long.class), eq(15L)))
                .thenReturn(1L);
        when(db.queryForObject(contains("dbo.hr_department_scopes"), eq(Long.class), any(Object[].class)))
                .thenReturn(1L);
        assertDoesNotThrow(() -> service.requireCreate(
                new AuthenticatedAccount(2L, 23L, "hr", "HR"), 15L, future));
    }

    @Test
    void employeeRoleCannotManageSchedules() throws Exception {
        mockCurrentActor("EMPLOYEE", 3L, 24L);
        assertEquals(HttpStatus.FORBIDDEN, assertThrows(ResponseStatusException.class,
                () -> service.requireCreate(new AuthenticatedAccount(3L, 24L, "emp", "EMPLOYEE"), 15L, future))
                .getStatusCode());
    }
}
