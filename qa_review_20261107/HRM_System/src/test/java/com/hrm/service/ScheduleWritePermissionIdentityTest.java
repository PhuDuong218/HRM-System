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

class ScheduleWritePermissionIdentityTest {
    private final JdbcTemplate db = mock(JdbcTemplate.class);
    private final ScheduleWritePermissionService service = new ScheduleWritePermissionService(db);
    private final LocalDateTime plannedStart = LocalDateTime.of(2030, 5, 1, 8, 0);

    @SuppressWarnings({ "rawtypes", "unchecked" })
    private void accountInDatabase(String role, Long employeeId) throws Exception {
        when(db.query(anyString(), any(RowMapper.class), eq(7L))).thenAnswer(invocation -> {
            RowMapper mapper = invocation.getArgument(1);
            ResultSet rs = mock(ResultSet.class);
            when(rs.getString(1)).thenReturn(role);
            when(rs.getObject(2, Long.class)).thenReturn(employeeId);
            return List.of(mapper.mapRow(rs, 0));
        });
    }

    private AuthenticatedAccount principal(String role, Long employeeId) {
        return new AuthenticatedAccount(7L, employeeId, "identity-test", role);
    }

    private void workingEmployee() {
        when(db.queryForObject(contains("dbo.employees"), eq(Long.class), eq(15L), any()))
                .thenReturn(1L);
    }

    private void forbidden(AuthenticatedAccount actor) {
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service.requireCreate(actor, 15L, plannedStart));
        assertEquals(HttpStatus.FORBIDDEN, ex.getStatusCode());
    }

    @Test
    void adminWithoutEmployeeIdInPrincipalAndDatabaseIsAccepted() throws Exception {
        accountInDatabase("ADMIN", null);
        workingEmployee();
        assertDoesNotThrow(() -> service.requireCreate(principal("ADMIN", null), 15L, plannedStart));
        verify(db, never()).queryForObject(contains("dbo.hr_department_scopes"), eq(Long.class), any(Object[].class));
    }

    @Test
    void adminWithMatchingEmployeeIdIsAccepted() throws Exception {
        accountInDatabase("ADMIN", 22L);
        workingEmployee();
        assertDoesNotThrow(() -> service.requireCreate(principal("ADMIN", 22L), 15L, plannedStart));
    }

    @Test
    void adminPrincipalNullButDatabaseEmployeeNotNullIsForbidden() throws Exception {
        accountInDatabase("ADMIN", 22L);
        forbidden(principal("ADMIN", null));
        verify(db, never()).queryForObject(contains("dbo.employees"), eq(Long.class), any(Object[].class));
    }

    @Test
    void adminPrincipalEmployeeButDatabaseNullIsForbidden() throws Exception {
        accountInDatabase("ADMIN", null);
        forbidden(principal("ADMIN", 22L));
    }

    @Test
    void adminWithDifferentEmployeeIdsIsForbidden() throws Exception {
        accountInDatabase("ADMIN", 23L);
        forbidden(principal("ADMIN", 22L));
    }

    @Test
    void hrPrincipalMissingEmployeeIdButDatabaseHasOneIsForbidden() throws Exception {
        accountInDatabase("HR", 23L);
        forbidden(principal("HR", null));
    }

    @Test
    void hrPrincipalEmployeeIdButDatabaseNullIsForbidden() throws Exception {
        accountInDatabase("HR", null);
        forbidden(principal("HR", 23L));
    }

    @Test
    void inactiveOrMissingAccountIsForbidden() {
        when(db.query(anyString(), any(RowMapper.class), eq(7L))).thenReturn(List.of());
        forbidden(principal("ADMIN", null));
    }

    @Test
    void changedRoleInDatabaseIsForbidden() throws Exception {
        accountInDatabase("EMPLOYEE", 22L);
        forbidden(principal("ADMIN", 22L));
    }
}
