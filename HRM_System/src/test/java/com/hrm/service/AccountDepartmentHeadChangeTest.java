package com.hrm.service;

import com.hrm.dto.AdminAccessDTOs.AccountHeadChange;
import com.hrm.dto.DepartmentHeadDTO;
import com.hrm.security.AuthenticatedAccount;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.web.server.ResponseStatusException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AccountDepartmentHeadChangeTest {
    private final LocalDate today = LocalDate.of(2026, 11, 18);
    private final AuthenticatedAccount admin = new AuthenticatedAccount(10L, 11L, "admin", "ADMIN");
    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final AssignmentGuardService guard = mock(AssignmentGuardService.class);
    private final DepartmentHeadAccessService access = mock(DepartmentHeadAccessService.class);
    private final DepartmentHeadService service = new DepartmentHeadService(jdbc, guard, access,
            Clock.fixed(Instant.parse("2026-11-18T00:00:00Z"), ZoneOffset.UTC));

    @Test
    void appointmentFromAccountCannotReplaceAnOverlappingHead() {
        when(jdbc.queryForList(startsWith("SELECT department_id"), eq(Long.class), eq(12L)))
                .thenReturn(List.of(3L));
        when(jdbc.queryForList(startsWith("SELECT is_active"), eq(Boolean.class), eq(3L)))
                .thenReturn(List.of(true));
        when(jdbc.queryForObject(contains("a.effective_to>?"), eq(Long.class), eq(3L), eq(today)))
                .thenReturn(1L);

        var error = assertThrows(ResponseStatusException.class,
                () -> service.changeFromAccount(12L, new AccountHeadChange(true, today, null, "Bổ nhiệm"), admin));

        assertEquals(HttpStatus.CONFLICT, error.getStatusCode());
        assertTrue(error.getReason().contains("đã có trưởng phòng"));
        var ordered = inOrder(guard, jdbc);
        ordered.verify(guard).lockTimeline();
        ordered.verify(jdbc).queryForList(startsWith("SELECT department_id"), eq(Long.class), eq(12L));
        verify(jdbc, never()).update(anyString(), any(Object[].class));
    }

    @Test
    void staleEditCannotEndAnAssignmentOwnedByAnotherEmployee() {
        when(jdbc.queryForList(startsWith("SELECT department_id"), eq(Long.class), eq(12L)))
                .thenReturn(List.of(3L));
        when(jdbc.queryForList(startsWith("SELECT is_active"), eq(Boolean.class), eq(3L)))
                .thenReturn(List.of(true));
        when(jdbc.queryForList(startsWith("SELECT position_code"), eq(String.class), eq(4L)))
                .thenReturn(List.of("NHAN_VIEN"));
        when(jdbc.query(contains("a.effective_to IS NULL"), org.mockito.ArgumentMatchers.<RowMapper<DepartmentHeadDTO>>any(), eq(3L)))
                .thenReturn(List.of(new DepartmentHeadDTO(1L, 3L, 13L, "NV13", "Trưởng phòng", today.minusDays(4), null)));

        var error = assertThrows(ResponseStatusException.class,
                () -> service.changeFromAccount(12L, new AccountHeadChange(false, today, 4L, "Kết thúc"), admin));

        assertEquals(HttpStatus.CONFLICT, error.getStatusCode());
        assertTrue(error.getReason().contains("đã thay đổi"));
        verify(jdbc, never()).update(anyString(), any(Object[].class));
    }
}
