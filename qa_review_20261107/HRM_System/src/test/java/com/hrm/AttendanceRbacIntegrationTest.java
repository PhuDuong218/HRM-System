package com.hrm;

import com.hrm.service.AttendancePermissionService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Assumptions;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import org.springframework.web.server.ResponseStatusException;
import java.sql.DriverManager;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Opt-in, read-only SQL Server test. Never starts the application context.
 * Required env: HRM_TEST_RBAC_IT=true, HRM_TEST_RBAC_URL (explicit hrm_test),
 * HRM_TEST_RBAC_USER and HRM_TEST_RBAC_PASSWORD.
 */
class AttendanceRbacIntegrationTest {
    @Test
    void seededPermissionsAndInactiveActors() throws Exception {
        Assumptions.assumeTrue("true".equalsIgnoreCase(System.getenv("HRM_TEST_RBAC_IT")),
                "Integration test was not opted in");
        String url = System.getenv("HRM_TEST_RBAC_URL");
        Assumptions.assumeTrue(url != null && url.matches("(?is).*databaseName\\s*=\\s*hrm_test(?:[; ]|$).*"),
                "Explicit hrm_test JDBC URL required");
        String user = System.getenv("HRM_TEST_RBAC_USER");
        String password = System.getenv("HRM_TEST_RBAC_PASSWORD");
        Assumptions.assumeTrue(user != null && password != null,
                "Integration credentials must be supplied through environment");
        try (var connection = DriverManager.getConnection(url, user, password)) {
            connection.setReadOnly(true); // Best-effort JDBC read-only hint; no DML in test.
            JdbcTemplate db = new JdbcTemplate(new SingleConnectionDataSource(connection, true));
            assertEquals("hrm_test", db.queryForObject("SELECT DB_NAME()", String.class));
            assertEquals(3L, db.queryForObject("""
                    SELECT COUNT_BIG(*) FROM dbo.permissions
                    WHERE permission_code IN ('ATTENDANCE_MANAGE','ATTENDANCE_CONFIRM','ATTENDANCE_UNCONFIRM')
                    """, Long.class));
            assertEquals(6L, db.queryForObject("""
                    SELECT COUNT_BIG(*) FROM dbo.role_permissions rp
                    JOIN dbo.roles r ON r.role_id=rp.role_id
                    JOIN dbo.permissions p ON p.permission_id=rp.permission_id
                    WHERE r.role_code IN ('ADMIN','HR')
                      AND p.permission_code IN ('ATTENDANCE_MANAGE','ATTENDANCE_CONFIRM','ATTENDANCE_UNCONFIRM')
                    """, Long.class));
            AttendancePermissionService svc = new AttendancePermissionService(db);
            for (long id : new long[] { 2L, 5L }) {
                assertEquals(0, db.queryForObject(
                        "SELECT COUNT(*) FROM dbo.accounts WHERE account_id=? AND is_active=1",
                        Integer.class, id));
                assertThrows(ResponseStatusException.class,
                        () -> svc.requirePermission(id, AttendancePermissionService.CONFIRM));
            }
        }
    }
}
