package com.hrm.service;

import com.hrm.security.AuthenticatedAccount;
import java.sql.ResultSet;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.web.server.ResponseStatusException;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class WorkShiftPermissionTest {
    private final JdbcTemplate db = mock(JdbcTemplate.class);
    private final WorkShiftPermissionService svc = new WorkShiftPermissionService(db);

    @SuppressWarnings({ "unchecked", "rawtypes" })
    private void role(String role, Long employeeId) throws Exception {
        when(db.query(anyString(), any(RowMapper.class), eq(10L))).thenAnswer(inv -> {
            ResultSet rs = mock(ResultSet.class);
            when(rs.getString(1)).thenReturn(role);
            when(rs.getObject(2, Long.class)).thenReturn(employeeId);
            return List.of(((RowMapper) inv.getArgument(1)).mapRow(rs, 0));
        });
    }

    @Test
    void adminWithoutEmployeeAllowed() throws Exception {
        role("ADMIN", null);
        assertDoesNotThrow(() -> svc.requireManage(new AuthenticatedAccount(10L, null, "admin", "ADMIN")));
    }

    @Test
    void hrAllowed() throws Exception {
        role("HR", 5L);
        assertDoesNotThrow(() -> svc.requireManage(new AuthenticatedAccount(10L, 5L, "hr", "HR")));
    }

    @Test
    void employeeDenied() throws Exception {
        role("EMPLOYEE", 5L);
        assertThrows(ResponseStatusException.class,
                () -> svc.requireManage(new AuthenticatedAccount(10L, 5L, "emp", "EMPLOYEE")));
    }

    @Test
    void differentEmployeeDenied() throws Exception {
        role("HR", 5L);
        assertThrows(ResponseStatusException.class,
                () -> svc.requireManage(new AuthenticatedAccount(10L, 6L, "hr", "HR")));
    }

    @Test
    void roleMismatchDenied() throws Exception {
        role("HR", 5L);
        assertThrows(ResponseStatusException.class,
                () -> svc.requireManage(new AuthenticatedAccount(10L, 5L, "hr", "ADMIN")));
    }

    @Test
    void noAccountDenied() {
        assertThrows(ResponseStatusException.class, () -> svc.requireManage(null));
    }
}
