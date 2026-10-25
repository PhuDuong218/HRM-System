package com.hrm;

import com.hrm.dto.AttendanceDTOs.AttendanceView;
import com.hrm.service.AttendancePermissionService;
import com.hrm.service.AttendanceService;
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

class AttendanceRbacScopeAndSeparationTest {
    private final LocalDate day = LocalDate.of(2026, 10, 22);

    private AttendanceView record() {
        return new AttendanceView(12L, 3L, 4L, day, null, null, 0, "VANG", "CHUA_XAC_NHAN", null, null, null);
    }

    private JdbcTemplate locked() {
        JdbcTemplate db = mock(JdbcTemplate.class);
        when(db.query(contains("WITH (UPDLOCK, HOLDLOCK)"),
                org.mockito.ArgumentMatchers.<RowMapper<AttendanceView>>any(), eq(12L))).thenReturn(List.of(record()));
        return db;
    }

    private AttendancePermissionService hr() {
        AttendancePermissionService p = mock(AttendancePermissionService.class);
        when(p.requireAttendanceAccess(5L, 3L, day, AttendancePermissionService.CONFIRM))
                .thenReturn(new AttendancePermissionService.Actor(5L, 2L, "HR"));
        return p;
    }

    private void forbidden(Runnable r) {
        assertEquals(HttpStatus.FORBIDDEN, assertThrows(ResponseStatusException.class, r::run).getStatusCode());
    }

    @Test
    void hrCannotConfirmLegacyWithoutCreator() {
        JdbcTemplate db = locked();
        forbidden(() -> new AttendanceService(db, hr()).confirm(12L, 5L));
        verify(db, never()).update(anyString(), any(Object[].class));
    }

    @Test
    void hrCannotConfirmOwnCreation() {
        JdbcTemplate db = locked();
        when(db.query(contains("SELECT created_by"), any(org.springframework.jdbc.core.ResultSetExtractor.class),
                eq(12L)))
                .thenReturn(5L);
        forbidden(() -> new AttendanceService(db, hr()).confirm(12L, 5L));
        verify(db, never()).update(anyString(), any(Object[].class));
    }

    @Test
    void hrCannotConfirmLastOwnCorrection() {
        JdbcTemplate db = locked();
        when(db.query(contains("SELECT created_by"), any(org.springframework.jdbc.core.ResultSetExtractor.class),
                eq(12L)))
                .thenReturn(9L);
        when(db.query(contains("ATTENDANCE_CORRECT"), any(org.springframework.jdbc.core.ResultSetExtractor.class),
                eq("12")))
                .thenReturn(5L);
        forbidden(() -> new AttendanceService(db, hr()).confirm(12L, 5L));
        verify(db, never()).update(anyString(), any(Object[].class));
    }

    @Test
    void deniedPermissionStopsBeforeApprovalQuery() {
        JdbcTemplate db = locked();
        AttendancePermissionService p = mock(AttendancePermissionService.class);
        when(p.requireAttendanceAccess(5L, 3L, day, AttendancePermissionService.CONFIRM))
                .thenThrow(new ResponseStatusException(HttpStatus.FORBIDDEN));
        forbidden(() -> new AttendanceService(db, p).confirm(12L, 5L));
        verify(db, never()).update(anyString(), any(Object[].class));
    }
}
