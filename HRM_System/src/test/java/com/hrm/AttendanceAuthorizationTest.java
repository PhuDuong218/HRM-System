package com.hrm;

import com.hrm.service.AttendanceService;
import com.hrm.service.AttendancePermissionService;
import com.hrm.dto.AttendanceDTOs.AttendanceCorrection;
import com.hrm.dto.AttendanceDTOs.AttendanceView;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import java.time.LocalDate;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Service-level authorization tests; no real database or Spring context. */
class AttendanceAuthorizationTest {
    private static AttendanceView record() {
        return new AttendanceView(12L, 3L, 4L, LocalDate.of(2026, 10, 22),
                null, null, 0, "VANG", "CHUA_XAC_NHAN", null, null, null);
    }

    private JdbcTemplate lockedRecord() {
        JdbcTemplate db = mock(JdbcTemplate.class);
        when(db.query(contains("WITH (UPDLOCK, HOLDLOCK)"),
                org.mockito.ArgumentMatchers.<RowMapper<AttendanceView>>any(), eq(12L)))
                .thenReturn(List.of(record()));
        return db;
    }

    @Test
    void confirmRejectedDoesNotWriteOrAudit() {
        JdbcTemplate db = lockedRecord();
        AttendancePermissionService permissions = mock(AttendancePermissionService.class);
        doThrow(new ResponseStatusException(HttpStatus.FORBIDDEN, "denied"))
                .when(permissions)
                .requireAttendanceAccess(1L, 3L, LocalDate.of(2026, 10, 22), AttendancePermissionService.CONFIRM);
        AttendanceService service = new AttendanceService(db, permissions);
        assertThrows(ResponseStatusException.class, () -> service.confirm(12L, 1L));
        verify(db, never()).update(anyString(), any(Object[].class));
        verify(permissions).requireAttendanceAccess(1L, 3L, LocalDate.of(2026, 10, 22),
                AttendancePermissionService.CONFIRM);
    }

    @Test
    void correctRejectedDoesNotWriteOrAudit() {
        JdbcTemplate db = lockedRecord();
        AttendancePermissionService permissions = mock(AttendancePermissionService.class);
        doThrow(new ResponseStatusException(HttpStatus.FORBIDDEN, "denied"))
                .when(permissions)
                .requireAttendanceAccess(1L, 3L, LocalDate.of(2026, 10, 22), AttendancePermissionService.MANAGE);
        AttendanceService service = new AttendanceService(db, permissions);
        AttendanceCorrection correction = new AttendanceCorrection(null, null, 0, "VANG", "reason");
        assertThrows(ResponseStatusException.class, () -> service.correct(12L, correction, 1L));
        verify(db, never()).update(anyString(), any(Object[].class));
        verify(permissions).requireAttendanceAccess(1L, 3L, LocalDate.of(2026, 10, 22),
                AttendancePermissionService.MANAGE);
    }

    @Test
    void missingPermissionServiceCannotCreateAttendanceService() {
        assertThrows(NullPointerException.class,
                () -> new AttendanceService(mock(JdbcTemplate.class), null));
    }
}
