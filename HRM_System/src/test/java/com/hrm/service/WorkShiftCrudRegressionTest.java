package com.hrm.service;

import com.hrm.dto.DetailedWorkTimeDTOs.*;
import com.hrm.dto.WorkTimeDTOs.*;
import com.hrm.security.AuthenticatedAccount;
import java.time.LocalTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.BadSqlGrammarException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.json.JsonMapper;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class WorkShiftCrudRegressionTest {
    private final AuthenticatedAccount actor = new AuthenticatedAccount(1L, 1L, "admin", "ADMIN");
    private final JdbcTemplate db = mock(JdbcTemplate.class);
    private final WorkShiftPermissionService permissions = mock(WorkShiftPermissionService.class);
    private final WorkShiftService shifts = mock(WorkShiftService.class);
    private final DetailedWorkTimeService detailed = mock(DetailedWorkTimeService.class);
    private final WorkShiftManagementWriteService service = new WorkShiftManagementWriteService(
            db, permissions, shifts, detailed, JsonMapper.builder().build());
    private final ShiftInput input = new ShiftInput("DAY", "Ca ngày", LocalTime.of(8, 0), LocalTime.of(17, 0), 0, 60, true);
    private final ShiftView view = new ShiftView(9, "DAY", "Ca ngày", LocalTime.of(8, 0), LocalTime.of(17, 0), 0, 60, true);
    private final ShiftWithBreaks request = new ShiftWithBreaks(input, List.of(new OffsetBreak(240, 300)));

    @Test void createsNonzeroBreakThroughDetailedService() {
        when(detailed.createShift(actor, request)).thenReturn(view);
        assertEquals(view, service.create(actor, request));
        verify(permissions).requireManage(actor);
        verify(detailed).createShift(actor, request);
        verify(shifts, never()).createWithVerifiedBreaks(any(), any());
    }

    @SuppressWarnings("unchecked")
    @Test void updatesDetailedTemplateAndRejectsChangingCode() {
        when(db.query(contains("FROM dbo.work_shifts WITH"), any(RowMapper.class), eq(9L))).thenReturn(List.of(view));
        when(detailed.updateShift(actor, 9, request)).thenReturn(view);
        assertEquals(view, service.update(actor, 9, request));
        var changed = new ShiftInput("OTHER", "Ca ngày", input.startTime(), input.endTime(), 0, 60, true);
        assertThrows(ResponseStatusException.class,
                () -> service.update(actor, 9, new ShiftWithBreaks(changed, request.breaks())));
        verify(detailed, times(1)).updateShift(eq(actor), eq(9L), any());
    }

    @Test void deniedActorCannotCreate() {
        doThrow(new ResponseStatusException(HttpStatus.FORBIDDEN)).when(permissions).requireManage(actor);
        assertThrows(ResponseStatusException.class, () -> service.create(actor, request));
        verifyNoInteractions(detailed);
    }

    @Test void blankLockReasonIsRejectedBeforeWrite() {
        assertThrows(ResponseStatusException.class, () -> service.setActive(actor, 9, false, "  "));
        verifyNoInteractions(db);
    }

    @SuppressWarnings("unchecked")
    @Test void locksShiftWithReasonAndWritesAudit() {
        var inactive = new ShiftView(9, view.shiftCode(), view.shiftName(), view.startTime(), view.endTime(), 0, 60, false);
        when(db.query(contains("FROM dbo.work_shifts WITH"), any(RowMapper.class), eq(9L)))
                .thenReturn(List.of(view), List.of(inactive));
        when(db.update(contains("SET is_active=?"), eq(false), eq(9L), eq(true))).thenReturn(1);
        assertFalse(service.setActive(actor, 9, false, "Không sử dụng nữa").active());
        verify(db).update(contains("INSERT INTO dbo.audit_logs"), eq(actor.accountId()),
                eq("WORK_SHIFT_DEACTIVATE"), eq("9"), eq("Không sử dụng nữa"), anyString(), anyString());
    }

    @SuppressWarnings("unchecked")
    @Test void missingBreakSchemaProduces503() {
        var real = new DetailedWorkTimeService(db, shifts, mock(WorkScheduleService.class));
        when(db.query(contains("FROM dbo.work_shift_breaks"), any(RowMapper.class), eq(9L)))
                .thenThrow(new BadSqlGrammarException("read", "SELECT", new java.sql.SQLException("Invalid object name")));
        var error = assertThrows(ResponseStatusException.class, () -> real.shiftBreaks(9));
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, error.getStatusCode());
        assertTrue(error.getReason().contains("004_work_shift_breaks.sql"));
    }

    @Test void overlappingBreaksRejectedBeforeDatabaseWrite() {
        var real = new DetailedWorkTimeService(db, shifts, mock(WorkScheduleService.class));
        var invalid = new ShiftWithBreaks(input, List.of(new OffsetBreak(240, 280), new OffsetBreak(270, 290)));
        assertThrows(ResponseStatusException.class, () -> real.createShift(actor, invalid));
        verifyNoInteractions(db, shifts);
    }
}
