package com.hrm;

import com.hrm.dto.AttendanceDTOs.AttendanceView;
import com.hrm.service.AttendancePermissionService;
import com.hrm.service.AttendanceService;
import java.time.LocalDate;
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

/**
 * Unit tests only: JdbcTemplate is mocked and no database connection is
 * created.
 */
class AttendanceUnconfirmTest {
    private static final long ID = 12L;
    private static final long EMPLOYEE = 3L;
    private static final long ADMIN_ACCOUNT = 7L;
    private static final long HR_ACCOUNT = 5L;
    private static final LocalDate WORK_DATE = LocalDate.of(2026, 10, 22);
    private final JdbcTemplate db = mock(JdbcTemplate.class);
    private final AttendancePermissionService permissions = mock(AttendancePermissionService.class);
    private final AttendanceService service = new AttendanceService(db, permissions);

    private AttendanceView record(String status, Long confirmedBy) {
        return new AttendanceView(ID, EMPLOYEE, 4L, WORK_DATE,
                LocalDateTime.of(2026, 10, 22, 8, 0),
                LocalDateTime.of(2026, 10, 22, 17, 0),
                60, "DI_LAM", status, confirmedBy,
                confirmedBy == null ? null : LocalDateTime.of(2026, 10, 22, 18, 0), null);
    }

    private void locked(AttendanceView current) {
        when(db.query(contains("WITH (UPDLOCK, HOLDLOCK)"),
                org.mockito.ArgumentMatchers.<RowMapper<AttendanceView>>any(), eq(ID)))
                .thenReturn(List.of(current));
    }

    private void permit(long account, String role) {
        when(permissions.requireAttendanceAccess(account, EMPLOYEE, WORK_DATE,
                AttendancePermissionService.UNCONFIRM))
                .thenReturn(new AttendancePermissionService.Actor(account,
                        "ADMIN".equals(role) ? null : 2L, role));
    }

    private void assertStatus(HttpStatus expected, org.junit.jupiter.api.function.Executable call) {
        assertEquals(expected, assertThrows(ResponseStatusException.class, call).getStatusCode());
    }

    private void successfulUpdate(long actor) {
        when(db.update(contains("UPDATE dbo.attendance_records"), eq(actor), eq(ID)))
                .thenReturn(1);
        when(db.queryForObject(contains("FOR JSON PATH"), eq(String.class), eq(ID)))
                .thenReturn("{\"verification_status\":\"DA_XAC_NHAN\"}",
                        "{\"verification_status\":\"CHUA_XAC_NHAN\"}");
        when(db.query(argThat((String sql) -> sql.contains("SELECT *")
                && !sql.contains("UPDLOCK")),
                org.mockito.ArgumentMatchers.<RowMapper<AttendanceView>>any(), eq(ID)))
                .thenReturn(List.of(record("CHUA_XAC_NHAN", null)));
    }

    @Test
    void adminCanUnconfirmAndAuditWithTrimmedReason() {
        locked(record("DA_XAC_NHAN", ADMIN_ACCOUNT));
        permit(ADMIN_ACCOUNT, "ADMIN");
        successfulUpdate(ADMIN_ACCOUNT);
        AttendanceView result = service.unconfirm(ID, "  Sai gio  ", ADMIN_ACCOUNT);
        assertEquals("CHUA_XAC_NHAN", result.verificationStatus());
        assertNull(result.confirmedBy());
        assertNull(result.confirmedAt());
        assertEquals(LocalDateTime.of(2026, 10, 22, 8, 0), result.actualStart());
        assertEquals(LocalDateTime.of(2026, 10, 22, 17, 0), result.actualEnd());
        verify(db).update(contains("confirmed_by = NULL"), eq(ADMIN_ACCOUNT), eq(ID));
        verify(db).update(contains("INSERT INTO dbo.audit_logs"), eq(ADMIN_ACCOUNT),
                eq("ATTENDANCE_UNCONFIRM"), eq("12"), eq("Sai gio"),
                contains("DA_XAC_NHAN"), contains("CHUA_XAC_NHAN"));
    }

    @Test
    void anotherHrCanUnconfirm() {
        locked(record("DA_XAC_NHAN", 9L));
        permit(HR_ACCOUNT, "HR");
        successfulUpdate(HR_ACCOUNT);
        assertEquals("CHUA_XAC_NHAN", service.unconfirm(ID, "Can sua", HR_ACCOUNT).verificationStatus());
    }

    @Test
    void hrCannotUnconfirmOwnApproval() {
        locked(record("DA_XAC_NHAN", HR_ACCOUNT));
        permit(HR_ACCOUNT, "HR");
        assertStatus(HttpStatus.FORBIDDEN, () -> service.unconfirm(ID, "Can sua", HR_ACCOUNT));
        verify(db, never()).update(anyString(), any(Object[].class));
    }

    @Test
    void permissionDenialStopsBeforeUpdate() {
        locked(record("DA_XAC_NHAN", 9L));
        when(permissions.requireAttendanceAccess(HR_ACCOUNT, EMPLOYEE, WORK_DATE,
                AttendancePermissionService.UNCONFIRM))
                .thenThrow(new ResponseStatusException(HttpStatus.FORBIDDEN));
        assertStatus(HttpStatus.FORBIDDEN, () -> service.unconfirm(ID, "Can sua", HR_ACCOUNT));
        verify(db, never()).update(anyString(), any(Object[].class));
    }

    @Test
    void missingAttendanceReturnsNotFound() {
        assertStatus(HttpStatus.NOT_FOUND, () -> service.unconfirm(ID, "Can sua", HR_ACCOUNT));
        verifyNoInteractions(permissions);
    }

    @Test
    void unconfirmedRecordReturnsConflict() {
        locked(record("CHUA_XAC_NHAN", null));
        permit(ADMIN_ACCOUNT, "ADMIN");
        assertStatus(HttpStatus.CONFLICT, () -> service.unconfirm(ID, "Can sua", ADMIN_ACCOUNT));
        verify(db, never()).update(anyString(), any(Object[].class));
    }

    @Test
    void nullReasonIsRejectedBeforeRead() {
        assertStatus(HttpStatus.BAD_REQUEST, () -> service.unconfirm(ID, null, HR_ACCOUNT));
        verifyNoInteractions(db, permissions);
    }

    @Test
    void blankReasonIsRejectedBeforeRead() {
        assertStatus(HttpStatus.BAD_REQUEST, () -> service.unconfirm(ID, "  ", HR_ACCOUNT));
        verifyNoInteractions(db, permissions);
    }

    @Test
    void zeroUpdatedRowsReturnConflictWithoutAudit() {
        locked(record("DA_XAC_NHAN", 9L));
        permit(ADMIN_ACCOUNT, "ADMIN");
        assertStatus(HttpStatus.CONFLICT, () -> service.unconfirm(ID, "Can sua", ADMIN_ACCOUNT));
        verify(db, never()).update(contains("INSERT INTO dbo.audit_logs"),
                any(), any(), any(), any(), any(), any());
    }

    @Test
    void auditFailureIsPropagatedForTransactionalRollback() {
        locked(record("DA_XAC_NHAN", 9L));
        permit(ADMIN_ACCOUNT, "ADMIN");
        successfulUpdate(ADMIN_ACCOUNT);
        when(db.update(contains("INSERT INTO dbo.audit_logs"), eq(ADMIN_ACCOUNT),
                eq("ATTENDANCE_UNCONFIRM"), eq("12"), eq("Can sua"),
                anyString(), anyString()))
                .thenThrow(new org.springframework.dao.DataIntegrityViolationException("audit unavailable"));
        assertThrows(org.springframework.dao.DataIntegrityViolationException.class,
                () -> service.unconfirm(ID, "Can sua", ADMIN_ACCOUNT));
        verify(db, never()).query(argThat((String sql) -> sql.contains("SELECT *") && !sql.contains("UPDLOCK")),
                org.mockito.ArgumentMatchers.<RowMapper<AttendanceView>>any(), eq(ID));
    }
}
