package com.hrm.service;

import com.hrm.dto.WorkTimeDTOs.ShiftInput;
import com.hrm.dto.WorkTimeDTOs.ShiftView;
import com.hrm.security.AuthenticatedAccount;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Checks valid shifts against the existing WorkShiftService using a mocked JDBC layer. */
class WorkShiftValidCasesTest {
    private JdbcTemplate db;
    private WorkShiftService service;
    private static final AuthenticatedAccount ADMIN = new AuthenticatedAccount(1L, null, "admin", "ADMIN");

    @BeforeEach
    void setup() {
        db = mock(JdbcTemplate.class);
        service = new WorkShiftService(db, mock(WorkShiftPermissionService.class));
    }

    private ShiftInput shift(String code, LocalTime start, LocalTime end, int offset, int rest) {
        return new ShiftInput(code, "Ca kiem thu", start, end, offset, rest, true);
    }

    /** Mocks only storage. The service's real validation and JDBC invocation still run. */
    private ShiftView createSuccessfully(ShiftInput input, long id) {
        ShiftView saved = new ShiftView(id, input.shiftCode().trim(), input.shiftName().trim(),
                input.startTime(), input.endTime(), input.endDayOffset(), input.breakMinutes(), input.active());
        when(db.queryForObject(startsWith("INSERT INTO dbo.work_shifts"), eq(Long.class),
                any(), any(), any(), any(), any(), any(), any())).thenReturn(id);
        when(db.query(startsWith("SELECT * FROM dbo.work_shifts WHERE shift_id="),
                org.mockito.ArgumentMatchers.<RowMapper<ShiftView>>any(), eq(id)))
                .thenReturn(List.of(saved));
        assertEquals(saved, service.create(ADMIN, input));
        verify(db).queryForObject(startsWith("INSERT INTO dbo.work_shifts"), eq(Long.class),
                eq(input.shiftCode().trim()), eq(input.shiftName().trim()), eq(input.startTime()),
                eq(input.endTime()), eq(input.endDayOffset()), eq(input.breakMinutes()), eq(input.active()));
        return saved;
    }

    @Test void normalShiftIsAccepted() {
        ShiftInput in = shift("DAY", LocalTime.of(8, 0), LocalTime.of(17, 0), 0, 60);
        ShiftView saved = createSuccessfully(in, 101L);
        assertEquals(0, saved.endDayOffset());
        assertEquals(60, saved.breakMinutes());
    }

    @Test void overnightShiftIsAccepted() {
        ShiftInput in = shift("NIGHT", LocalTime.of(22, 0), LocalTime.of(6, 0), 1, 30);
        ShiftView saved = createSuccessfully(in, 102L);
        assertEquals(1, saved.endDayOffset());
        assertEquals(LocalTime.of(6, 0), saved.endTime());
    }

    @Test void shiftWithNoBreakIsAccepted() {
        ShiftInput in = shift("SHORT", LocalTime.of(8, 0), LocalTime.of(12, 0), 0, 0);
        assertEquals(0, createSuccessfully(in, 103L).breakMinutes());
    }

    @Test void adjacentIntervalsDoNotOverlapAtBoundary() {
        LocalDate day = LocalDate.of(2026, 10, 23);
        LocalDateTime firstStart = day.atTime(8, 0);
        LocalDateTime firstEnd = day.atTime(12, 0);
        LocalDateTime secondStart = day.atTime(12, 0);
        LocalDateTime secondEnd = day.atTime(17, 0);
        assertFalse(firstStart.isBefore(secondEnd) && firstEnd.isAfter(secondStart));
        // Pure interval check; this is not an integration test of WorkScheduleService.
    }

    @Test void overnightIntervalOverlapsNextMorning() {
        LocalDate day = LocalDate.of(2026, 10, 23);
        LocalDateTime start = day.atTime(22, 0);
        LocalDateTime end = day.plusDays(1).atTime(6, 0);
        LocalDateTime nextStart = day.plusDays(1).atTime(5, 0);
        LocalDateTime nextEnd = day.plusDays(1).atTime(9, 0);
        assertTrue(start.isBefore(nextEnd) && end.isAfter(nextStart));
        // Pure interval check; this is not an integration test of WorkScheduleService.
    }

    @Test void netMinutesOfNormalShift() {
        long gross = Duration.between(LocalTime.of(8, 0), LocalTime.of(17, 0)).toMinutes();
        assertEquals(480L, gross - 60);
        // Independent expected-value example; WorkShiftService does not expose a net-minutes API.
    }

    @Test void netMinutesOfOvernightShift() {
        long gross = Duration.between(LocalTime.of(22, 0), LocalTime.of(6, 0)).toMinutes() + 1440;
        assertEquals(450L, gross - 30);
    }

    @Test void updateOfExistingShiftPreservesRequestedFields() {
        ShiftInput in = shift("NIGHT_UPDATED", LocalTime.of(21, 0), LocalTime.of(5, 0), 1, 45);
        ShiftView saved = new ShiftView(201L, in.shiftCode(), in.shiftName(), in.startTime(),
                in.endTime(), in.endDayOffset(), in.breakMinutes(), in.active());
        when(db.query(startsWith("SELECT * FROM dbo.work_shifts WITH (UPDLOCK, HOLDLOCK)"),
                org.mockito.ArgumentMatchers.<RowMapper<ShiftView>>any(), eq(201L)))
                .thenReturn(List.of(new ShiftView(201L, "OLD", "Ca cũ", in.startTime(), in.endTime(), in.endDayOffset(), in.breakMinutes(), true)));
        when(db.update(startsWith("UPDATE dbo.work_shifts SET"), any(), any(), any(), any(),
                any(), any(), any(), eq(201L))).thenReturn(1);
        when(db.query(startsWith("SELECT * FROM dbo.work_shifts WHERE shift_id="),
                org.mockito.ArgumentMatchers.<RowMapper<ShiftView>>any(), eq(201L)))
                .thenReturn(List.of(saved));
        assertEquals(saved, service.update(ADMIN, 201L, in));
        verify(db).update(startsWith("UPDATE dbo.work_shifts SET"),
                eq(in.shiftCode()), eq(in.shiftName()), eq(in.startTime()), eq(in.endTime()),
                eq(in.endDayOffset()), eq(in.breakMinutes()), eq(in.active()), eq(201L));
    }
}
