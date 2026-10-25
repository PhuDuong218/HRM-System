package com.hrm;

import com.hrm.service.AttendancePermissionService;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.web.server.ResponseStatusException;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AttendancePermissionServiceTest {
    private static final LocalDate DAY = LocalDate.of(2026, 10, 22);

    private JdbcTemplate db(long id, Long employee, String role, long grant) {
        JdbcTemplate db = mock(JdbcTemplate.class);
        when(db.query(anyString(), org.mockito.ArgumentMatchers.<RowMapper<AttendancePermissionService.Actor>>any(),
                eq(id)))
                .thenReturn(List.of(new AttendancePermissionService.Actor(id, employee, role)));
        when(db.queryForObject(contains("role_permissions"), eq(Long.class), eq(id), anyString()))
                .thenReturn(grant);
        return db;
    }

    private void denied(Runnable action) {
        assertEquals(HttpStatus.FORBIDDEN, assertThrows(ResponseStatusException.class, action::run).getStatusCode());
    }

    @Test
    void invalidInputDoesNotReachDb() {
        JdbcTemplate db = mock(JdbcTemplate.class);
        var p = new AttendancePermissionService(db);
        assertThrows(ResponseStatusException.class, () -> p.requirePermission(0, AttendancePermissionService.CONFIRM));
        assertThrows(ResponseStatusException.class, () -> p.requirePermission(1, "UNKNOWN"));
        verifyNoInteractions(db);
    }

    @Test
    void inactiveAccountDenied() {
        JdbcTemplate db = mock(JdbcTemplate.class);
        denied(() -> new AttendancePermissionService(db).requirePermission(5, AttendancePermissionService.CONFIRM));
    }

    @Test
    void employeeCannotManage() {
        JdbcTemplate db = db(3, 3L, "EMPLOYEE", 1);
        denied(() -> new AttendancePermissionService(db).requirePermission(3, AttendancePermissionService.MANAGE));
    }

    @Test
    void missingGrantDenied() {
        JdbcTemplate db = db(5, 2L, "HR", 0);
        denied(() -> new AttendancePermissionService(db).requirePermission(5, AttendancePermissionService.MANAGE));
    }

    @Test
    void adminNullEmployeeCanManage() {
        JdbcTemplate db = db(2, null, "ADMIN", 1);
        assertEquals("ADMIN", new AttendancePermissionService(db)
                .requirePermission(2, AttendancePermissionService.MANAGE).roleCode());
    }

    @Test
    void hrNullEmployeeDenied() {
        JdbcTemplate db = db(5, null, "HR", 1);
        denied(() -> new AttendancePermissionService(db).requirePermission(5, AttendancePermissionService.MANAGE));
    }

    @Test
    void adminCannotConfirmOwnAttendance() {
        JdbcTemplate db = db(2, 3L, "ADMIN", 1);
        denied(() -> new AttendancePermissionService(db).requireAttendanceAccess(2, 3, DAY,
                AttendancePermissionService.CONFIRM));
    }

    @Test
    void hrCannotManageOwnAttendance() {
        JdbcTemplate db = db(5, 3L, "HR", 1);
        denied(() -> new AttendancePermissionService(db).requireAttendanceAccess(5, 3, DAY,
                AttendancePermissionService.MANAGE));
    }

    @Test
    void adminCannotManageOwnAttendance() {
        JdbcTemplate db = db(2, 3L, "ADMIN", 1);
        denied(() -> new AttendancePermissionService(db).requireAttendanceAccess(2, 3, DAY,
                AttendancePermissionService.MANAGE));
    }

    @Test
    void hrCannotUnconfirmOwnAttendance() {
        JdbcTemplate db = db(5, 3L, "HR", 1);
        denied(() -> new AttendancePermissionService(db).requireAttendanceAccess(5, 3, DAY,
                AttendancePermissionService.UNCONFIRM));
    }

    @Test
    void adminCanManageEligibleEmployee() {
        JdbcTemplate db = db(2, null, "ADMIN", 1);
        when(db.queryForObject(contains("dbo.employees"), eq(Long.class), eq(3L), eq(DAY))).thenReturn(1L);
        assertEquals("ADMIN", new AttendancePermissionService(db)
                .requireAttendanceAccess(2, 3, DAY, AttendancePermissionService.MANAGE).roleCode());
    }

    @Test
    void hrCannotAccessIneligibleEmployee() {
        JdbcTemplate db = db(5, 2L, "HR", 1);
        when(db.queryForObject(contains("dbo.employees"), eq(Long.class), eq(3L), eq(DAY))).thenReturn(0L);
        denied(() -> new AttendancePermissionService(db).requireAttendanceAccess(5, 3, DAY,
                AttendancePermissionService.MANAGE));
    }

    @Test
    void hrCannotManageWithoutActiveEmployeeAccount() {
        JdbcTemplate db = db(5, 2L, "HR", 1);
        when(db.queryForObject(contains("dbo.employees"), eq(Long.class), eq(3L), eq(DAY))).thenReturn(1L);
        denied(() -> new AttendancePermissionService(db).requireAttendanceAccess(5, 3, DAY,
                AttendancePermissionService.MANAGE));
    }

    @Test
    void hrCannotManageWithoutScope() {
        JdbcTemplate db = db(5, 2L, "HR", 1);
        when(db.queryForObject(contains("dbo.employees"), eq(Long.class), eq(3L), eq(DAY))).thenReturn(1L);
        when(db.queryForObject(contains("r.role_code='EMPLOYEE'"), eq(Long.class), eq(3L))).thenReturn(1L);
        denied(() -> new AttendancePermissionService(db).requireAttendanceAccess(5, 3, DAY,
                AttendancePermissionService.MANAGE));
    }

    @Test
    void hrCanManageScopedEmployee() {
        JdbcTemplate db = db(5, 2L, "HR", 1);
        when(db.queryForObject(contains("dbo.employees"), eq(Long.class), eq(3L), eq(DAY))).thenReturn(1L);
        when(db.queryForObject(contains("r.role_code='EMPLOYEE'"), eq(Long.class), eq(3L))).thenReturn(1L);
        when(db.queryForObject(contains("dbo.hr_department_scopes"), eq(Long.class), eq(3L), eq(DAY), eq(DAY), eq(5L)))
                .thenReturn(1L);
        assertEquals("HR", new AttendancePermissionService(db)
                .requireAttendanceAccess(5, 3, DAY, AttendancePermissionService.MANAGE).roleCode());
    }
}
