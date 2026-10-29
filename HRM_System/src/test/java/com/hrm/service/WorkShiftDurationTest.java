package com.hrm.service;

import com.hrm.security.AuthenticatedAccount;
import com.hrm.dto.WorkTimeDTOs.*;
import java.time.LocalTime;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

class WorkShiftDurationTest {
    private final JdbcTemplate db = mock(JdbcTemplate.class);
    private final WorkShiftService service = new WorkShiftService(db, mock(WorkShiftPermissionService.class));
    private final AuthenticatedAccount admin = new AuthenticatedAccount(1L, null, "admin", "ADMIN");

    private ShiftInput shift(int minute, int offset) {
        return new ShiftInput("A", "A", LocalTime.of(8, 0), LocalTime.of(8, minute), offset, 0, true);
    }

    @Test
    void longerThan24HoursRejected() {
        assertThrows(IllegalArgumentException.class, () -> service.create(admin, shift(1, 1)));
        verifyNoInteractions(db);
    }

    @Test
    void exactly24HoursPassesValidation() {
        when(db.queryForObject(startsWith("INSERT INTO dbo.work_shifts"), eq(Long.class), any(), any(), any(), any(),
                any(), any(), any())).thenReturn(3L);
        assertThrows(org.springframework.web.server.ResponseStatusException.class,
                () -> service.create(admin, shift(0, 1)));
        verify(db).queryForObject(startsWith("INSERT INTO dbo.work_shifts"), eq(Long.class), any(), any(), any(), any(),
                any(), any(), any());
    }
}
