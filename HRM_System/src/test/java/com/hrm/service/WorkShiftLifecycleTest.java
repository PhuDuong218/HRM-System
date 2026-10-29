package com.hrm.service;

import com.hrm.security.AuthenticatedAccount;
import com.hrm.dto.WorkTimeDTOs.*;
import java.time.LocalTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.web.server.ResponseStatusException;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class WorkShiftLifecycleTest {
    private final JdbcTemplate db = mock(JdbcTemplate.class);
    private final WorkShiftService svc = new WorkShiftService(db, mock(WorkShiftPermissionService.class));
    private final AuthenticatedAccount admin = new AuthenticatedAccount(1L, null, "admin", "ADMIN");
    private final ShiftView old = new ShiftView(9L, "A", "A", LocalTime.of(8, 0), LocalTime.of(17, 0), 0, 60, true);

    @SuppressWarnings("unchecked")
    private void locked() {
        when(db.query(startsWith("SELECT * FROM dbo.work_shifts WITH"), any(RowMapper.class), eq(9L)))
                .thenReturn(List.of(old));
    }

    @Test
    void referencedShiftDeactivated() {
        locked();
        when(db.queryForObject(contains("COUNT_BIG"), eq(Long.class), eq(9L))).thenReturn(1L);
        svc.deleteOrDeactivate(admin, 9L);
        verify(db).update(contains("SET is_active=0"), eq(9L));
        verify(db, never()).update(startsWith("DELETE FROM"), eq(9L));
    }

    @Test
    void unreferencedShiftDeactivatedInsteadOfDeleted() {
        locked();
        svc.deleteOrDeactivate(admin, 9L);
        verify(db).update(contains("SET is_active=0"), eq(9L));
        verify(db, never()).update(startsWith("DELETE FROM"), eq(9L));
    }

    @Test
    void changingReferencedTimeDenied() {
        locked();
        when(db.queryForObject(contains("COUNT_BIG"), eq(Long.class), eq(9L))).thenReturn(2L);
        var changed = new ShiftInput("A", "A", LocalTime.of(9, 0), LocalTime.of(17, 0), 0, 60, true);
        assertThrows(ResponseStatusException.class, () -> svc.update(admin, 9L, changed));
        verify(db, never()).update(startsWith("UPDATE dbo.work_shifts SET"), any(), any(), any(), any(), any(), any(),
                any(), any());
    }
}
