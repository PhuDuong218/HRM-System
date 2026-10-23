package com.hrm;

import com.hrm.service.AttendancePermissionService;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.web.server.ResponseStatusException;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AttendancePermissionServiceTest {
    private JdbcTemplate database(long actorId, long employeeId, String role, long grants) {
        JdbcTemplate db = mock(JdbcTemplate.class);
        when(db.query(anyString(), org.mockito.ArgumentMatchers.<RowMapper<AttendancePermissionService.Actor>>any(),
                eq(actorId)))
                .thenReturn(List.of(new AttendancePermissionService.Actor(actorId, employeeId, role)));
        when(db.queryForObject(anyString(), eq(Long.class), eq(actorId), anyString())).thenReturn(grants);
        return db;
    }

    private static void forbidden(Runnable operation) {
        assertEquals(HttpStatus.FORBIDDEN,
                assertThrows(ResponseStatusException.class, operation::run).getStatusCode());
    }

    @Test
    void invalidInputDoesNotReachDb() {
        JdbcTemplate db = mock(JdbcTemplate.class);
        var svc = new AttendancePermissionService(db);
        assertThrows(ResponseStatusException.class,
                () -> svc.requirePermission(0, AttendancePermissionService.CONFIRM));
        assertThrows(ResponseStatusException.class, () -> svc.requirePermission(5, "SOME_OTHER_PERMISSION"));
        verifyNoInteractions(db);
    }

    @Test
    void inactiveAccountDenied() {
        JdbcTemplate db = mock(JdbcTemplate.class);
        when(db.query(anyString(), org.mockito.ArgumentMatchers.<RowMapper<AttendancePermissionService.Actor>>any(),
                eq(5L))).thenReturn(List.of());
        forbidden(() -> new AttendancePermissionService(db).requirePermission(5, AttendancePermissionService.CONFIRM));
    }

    @Test
    void employeeRoleDeniedEvenIfGrantExists() {
        JdbcTemplate db = database(1, 3, "EMPLOYEE", 1);
        forbidden(() -> new AttendancePermissionService(db).requirePermission(1, AttendancePermissionService.CONFIRM));
    }

    @Test
    void absentGrantDenied() {
        JdbcTemplate db = database(5, 2, "HR", 0);
        forbidden(() -> new AttendancePermissionService(db).requirePermission(5, AttendancePermissionService.CONFIRM));
    }

    @Test
    void adminWithGrantAllowed() {
        JdbcTemplate db = database(2, 1, "ADMIN", 1);
        assertEquals("ADMIN", new AttendancePermissionService(db)
                .requirePermission(2, AttendancePermissionService.CONFIRM).roleCode());
    }

    @Test
    void adminCannotApproveOwnAttendance() {
        JdbcTemplate db = database(2, 1, "ADMIN", 1);
        forbidden(() -> new AttendancePermissionService(db).requireAttendanceAccess(2, 1,
                AttendancePermissionService.CONFIRM));
    }

    @Test
    void adminCanApproveOtherEmployeeRole() {
        JdbcTemplate db = database(2, 1, "ADMIN", 1);
        assertDoesNotThrow(() -> new AttendancePermissionService(db).requireAttendanceAccess(2, 2,
                AttendancePermissionService.CONFIRM));
    }

    @Test
    void hrCannotApproveSelf() {
        JdbcTemplate db = database(5, 2, "HR", 1);
        forbidden(() -> new AttendancePermissionService(db).requireAttendanceAccess(5, 2,
                AttendancePermissionService.CONFIRM));
    }

    @Test
    void hrCannotApproveOtherHr() {
        JdbcTemplate db = database(5, 2, "HR", 1);
        when(db.query(anyString(), org.mockito.ArgumentMatchers.<RowMapper<String>>any(), eq(7L)))
                .thenReturn(List.of("HR"));
        forbidden(() -> new AttendancePermissionService(db).requireAttendanceAccess(5, 7,
                AttendancePermissionService.CONFIRM));
    }

    @Test
    void hrAllowedOnlyOneEmployeeRoleMapping() {
        JdbcTemplate db = database(5, 2, "HR", 1);
        when(db.query(anyString(), org.mockito.ArgumentMatchers.<RowMapper<String>>any(), eq(3L)))
                .thenReturn(List.of("EMPLOYEE"));
        assertDoesNotThrow(() -> new AttendancePermissionService(db).requireAttendanceAccess(5, 3,
                AttendancePermissionService.CONFIRM));
    }

    @Test
    void hrCannotApproveMissingTargetAccount() {
        JdbcTemplate db = database(5, 2, "HR", 1);
        when(db.query(anyString(), org.mockito.ArgumentMatchers.<RowMapper<String>>any(), eq(3L)))
                .thenReturn(List.of());
        forbidden(() -> new AttendancePermissionService(db).requireAttendanceAccess(5, 3,
                AttendancePermissionService.CONFIRM));
    }

    @Test
    void hrCannotApproveAmbiguousAccounts() {
        JdbcTemplate db = database(5, 2, "HR", 1);
        when(db.query(anyString(), org.mockito.ArgumentMatchers.<RowMapper<String>>any(), eq(3L)))
                .thenReturn(List.of("EMPLOYEE", "HR"));
        forbidden(() -> new AttendancePermissionService(db).requireAttendanceAccess(5, 3,
                AttendancePermissionService.CONFIRM));
    }

    @Test
    void hrManageScopeNotYetApproved() {
        JdbcTemplate db = database(5, 2, "HR", 1);
        forbidden(() -> new AttendancePermissionService(db).requireAttendanceAccess(5, 3,
                AttendancePermissionService.MANAGE));
    }
}
