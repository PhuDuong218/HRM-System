package com.hrm.service;

import com.hrm.security.AuthenticatedAccount;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.web.server.ResponseStatusException;
import java.time.LocalDate;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import java.util.List;
import com.hrm.dto.WorkTimeDTOs.ScheduleView;

class ScheduleQueryServiceTest {
    private final JdbcTemplate db = mock(JdbcTemplate.class);
    private final ScheduleQueryService service = new ScheduleQueryService(db);
    private final LocalDate day = LocalDate.of(2026, 10, 23);

    @Test
    void anonymousCannotQuery() {
        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> service.search(null, null, null, day, day, 0, true));
        assertEquals(HttpStatus.FORBIDDEN, error.getStatusCode());
        verifyNoInteractions(db);
    }

    @Test
    void employeeCannotUseManagementQuery() {
        var actor = new AuthenticatedAccount(1L, 11L, "emp", "EMPLOYEE");
        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> service.search(actor, 22L, null, day, day, 0, false));
        assertEquals(HttpStatus.FORBIDDEN, error.getStatusCode());
        verifyNoInteractions(db);
    }

    @Test
    void invalidPageRejected() {
        var actor = new AuthenticatedAccount(1L, 11L, "admin", "ADMIN");
        assertEquals(HttpStatus.BAD_REQUEST, assertThrows(ResponseStatusException.class,
                () -> service.search(actor, null, null, day, day, -1, false)).getStatusCode());
        verifyNoInteractions(db);
    }

    @Test
    void paginationAlwaysUsesTenRows() {
        var actor = new AuthenticatedAccount(2L, 11L, "admin", "ADMIN");
        when(db.queryForObject(anyString(), eq(Long.class), any(Object[].class))).thenReturn(105L);
        when(db.query(anyString(), ArgumentMatchers.<RowMapper<ScheduleView>>any(), any(Object[].class)))
                .thenReturn(List.<ScheduleView>of());
        var result = service.search(actor, null, null, day, day, 2, false);
        assertEquals(10, result.size());
        assertEquals(11, result.totalPages());
        assertEquals(105L, result.totalElements());
        var arguments = ArgumentCaptor.forClass(Object[].class);
        verify(db).query(anyString(), ArgumentMatchers.<RowMapper<ScheduleView>>any(), arguments.capture());
        var sqlArgs = arguments.getValue();
        assertEquals(20L, sqlArgs[sqlArgs.length - 2]);
        assertEquals(10, sqlArgs[sqlArgs.length - 1]);
    }

    @Test
    void invalidDateRangeRejected() {
        var actor = new AuthenticatedAccount(1L, 11L, "admin", "ADMIN");
        assertEquals(HttpStatus.BAD_REQUEST, assertThrows(ResponseStatusException.class,
                () -> service.search(actor, null, null, day.plusDays(1), day, 0, false)).getStatusCode());
        verifyNoInteractions(db);
    }
}
