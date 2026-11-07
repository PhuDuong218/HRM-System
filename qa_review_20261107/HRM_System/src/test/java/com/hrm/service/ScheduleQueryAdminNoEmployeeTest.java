package com.hrm.service;

import com.hrm.dto.WorkTimeDTOs.ScheduleView;
import com.hrm.security.AuthenticatedAccount;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ScheduleQueryAdminNoEmployeeTest {
    private final JdbcTemplate db = mock(JdbcTemplate.class);
    private final ScheduleQueryService service = new ScheduleQueryService(db);
    private final LocalDate day = LocalDate.of(2026, 10, 24);

    private void stubEmptySearch() {
        when(db.queryForObject(anyString(), eq(Long.class), any(Object[].class))).thenReturn(0L);
        when(db.query(anyString(), org.mockito.ArgumentMatchers.<RowMapper<ScheduleView>>any(),
                any(Object[].class))).thenReturn(List.of());
    }

    @Test
    void adminWithoutEmployeeIdCanSearchAllSchedules() {
        stubEmptySearch();
        var admin = new AuthenticatedAccount(2L, null, "admin.sample", "ADMIN");
        var result = service.search(admin, null, null, day, day, 0, false);
        assertEquals(10, result.size());
        var sql = ArgumentCaptor.forClass(String.class);
        var params = ArgumentCaptor.forClass(Object[].class);
        verify(db).queryForObject(sql.capture(), eq(Long.class), params.capture());
        assertFalse(sql.getValue().contains("hr_department_scopes"));
        assertArrayEquals(new Object[] { day, day }, params.getValue());
    }

    @Test
    void adminWithoutEmployeeIdCanReadScheduleDetail() {
        var admin = new AuthenticatedAccount(2L, null, "admin.sample", "ADMIN");
        var view = new ScheduleView(77L, 13L, null, day,
                day.atTime(8, 0), day.atTime(12, 0), 0, "test");
        when(db.query(anyString(), org.mockito.ArgumentMatchers.<RowMapper<ScheduleView>>any(),
                any(Object[].class))).thenReturn(List.of(view));
        assertEquals(view, service.getAuthorized(admin, 77L));
        var params = ArgumentCaptor.forClass(Object[].class);
        verify(db).query(anyString(), org.mockito.ArgumentMatchers.<RowMapper<ScheduleView>>any(), params.capture());
        assertArrayEquals(new Object[] { 77L }, params.getValue());
    }

    @Test
    void hrWithoutEmployeeIdIsDenied() {
        assertForbiddenWithoutDbAccess(new AuthenticatedAccount(5L, null, "hr", "HR"));
    }

    @Test
    void employeeWithoutEmployeeIdIsDenied() {
        assertForbiddenWithoutDbAccess(new AuthenticatedAccount(9L, null, "emp", "EMPLOYEE"));
    }

    @Test
    void missingAccountIdIsDenied() {
        assertForbiddenWithoutDbAccess(new AuthenticatedAccount(null, null, "admin", "ADMIN"));
    }

    @Test
    void unsupportedRoleIsDenied() {
        assertForbiddenWithoutDbAccess(new AuthenticatedAccount(8L, null, "other", "OTHER"));
    }

    @Test
    void anonymousActorIsDenied() {
        assertForbiddenWithoutDbAccess(null);
    }

    private void assertForbiddenWithoutDbAccess(AuthenticatedAccount actor) {
        var error = assertThrows(ResponseStatusException.class,
                () -> service.search(actor, null, null, day, day, 0, false));
        assertEquals(HttpStatus.FORBIDDEN, error.getStatusCode());
        verifyNoInteractions(db);
    }
}
