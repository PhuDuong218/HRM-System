package com.hrm.service;

import com.hrm.dto.WorkTimeDTOs.ShiftInput;
import com.hrm.security.AuthenticatedAccount;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.LocalTime;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * Regression checks for the existing shift-time validation; no SQL Server
 * connection or DML.
 */
class WorkShiftValidationTest {
    private JdbcTemplate jdbc;
    private WorkShiftService service;
    private WorkShiftPermissionService permissions;
    private static final AuthenticatedAccount ADMIN = new AuthenticatedAccount(1L, null, "admin", "ADMIN");

    @BeforeEach
    void setUp() {
        jdbc = mock(JdbcTemplate.class);
        permissions = mock(WorkShiftPermissionService.class);
        service = new WorkShiftService(jdbc, permissions);
    }

    private ShiftInput input(LocalTime start, LocalTime end, int offset, int restMinutes) {
        return new ShiftInput("TEST_SHIFT", "Ca kiểm thử", start, end, offset, restMinutes, true);
    }

    private void rejected(ShiftInput input) {
        assertThrows(IllegalArgumentException.class, () -> service.create(ADMIN, input));
        verifyNoInteractions(jdbc);
    }

    @Test
    void rejectsZeroLengthSameDayShift() {
        rejected(input(LocalTime.of(8, 0), LocalTime.of(8, 0), 0, 0));
    }

    @Test
    void rejectsBackwardSameDayShift() {
        rejected(input(LocalTime.of(22, 0), LocalTime.of(6, 0), 0, 0));
    }

    @Test
    void rejectsNegativeDayOffset() {
        rejected(input(LocalTime.of(8, 0), LocalTime.of(17, 0), -1, 0));
    }

    @Test
    void rejectsMoreThanOneDayOffset() {
        rejected(input(LocalTime.of(8, 0), LocalTime.of(17, 0), 2, 0));
    }

    @Test
    void rejectsNegativeBreakMinutes() {
        rejected(input(LocalTime.of(8, 0), LocalTime.of(17, 0), 0, -1));
    }

    @Test
    void rejectsBreakLongerThanShift() {
        rejected(input(LocalTime.of(8, 0), LocalTime.of(17, 0), 0, 541));
    }

    @Test
    void rejectsBreakLongerThanOvernightShift() {
        rejected(input(LocalTime.of(22, 0), LocalTime.of(6, 0), 1, 481));
    }

    @Test
    void rejectsMissingStartTime() {
        rejected(input(null, LocalTime.of(17, 0), 0, 0));
    }

    @Test
    void rejectsMissingEndTime() {
        rejected(input(LocalTime.of(8, 0), null, 0, 0));
    }

    @Test
    void rejectsBlankShiftCode() {
        rejected(new ShiftInput(" ", "Ca kiểm thử", LocalTime.of(8, 0), LocalTime.of(17, 0), 0, 30, true));
    }
}
