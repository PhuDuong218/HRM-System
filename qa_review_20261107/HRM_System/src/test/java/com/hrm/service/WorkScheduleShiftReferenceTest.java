package com.hrm.service;

import com.hrm.security.AuthenticatedAccount;
import com.hrm.dto.WorkTimeDTOs.*;
import java.lang.reflect.Method;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.server.ResponseStatusException;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class WorkScheduleShiftReferenceTest {
    private final JdbcTemplate db = mock(JdbcTemplate.class);
    private final WorkScheduleService service = new WorkScheduleService(db, mock(ScheduleWritePermissionService.class));

    private void check(Long id) throws Exception {
        Method m = WorkScheduleService.class.getDeclaredMethod("requireActiveShift", long.class);
        m.setAccessible(true);
        try {
            m.invoke(service, id.longValue());
        } catch (java.lang.reflect.InvocationTargetException x) {
            throw (RuntimeException) x.getCause();
        }
    }

    @Test
    void activeAccepted() throws Exception {
        when(db.query(contains("FROM dbo.work_shifts WITH"),
                any(org.springframework.jdbc.core.ResultSetExtractor.class), eq(3L))).thenReturn(true);
        assertDoesNotThrow(() -> check(3L));
    }

    @Test
    void inactiveRejected() {
        when(db.query(contains("FROM dbo.work_shifts WITH"),
                any(org.springframework.jdbc.core.ResultSetExtractor.class), eq(3L))).thenReturn(false);
        assertThrows(ResponseStatusException.class, () -> check(3L));
    }

    @Test
    void missingRejected() {
        assertThrows(ResponseStatusException.class, () -> check(3L));
    }
}
