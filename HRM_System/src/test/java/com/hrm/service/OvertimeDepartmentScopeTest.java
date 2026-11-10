package com.hrm.service;

import com.hrm.security.AuthenticatedAccount;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.server.ResponseStatusException;
import static org.junit.jupiter.api.Assertions.*;

public class OvertimeDepartmentScopeTest {
    private JdbcTemplate db;
    private OvertimePermissionService permissions;
    private final AuthenticatedAccount hr = new AuthenticatedAccount(1L, 101L, "hr", "HR");

    public static Timestamp utcNow() { return Timestamp.from(Instant.now()); }

    @BeforeEach void setup() {
        JdbcDataSource source = new JdbcDataSource();
        source.setURL("jdbc:h2:mem:ot_scope;MODE=MSSQLServer;DB_CLOSE_DELAY=-1");
        db = new JdbcTemplate(source) {
            @Override public <T> T queryForObject(String sql, Class<T> type, Object... args) {
                return super.queryForObject(sql.replace("COUNT_BIG(*)", "COUNT(*)"), type, args);
            }
        };
        db.execute("DROP ALL OBJECTS");
        db.execute("CREATE SCHEMA dbo");
        db.execute("CREATE ALIAS SYSUTCDATETIME FOR 'com.hrm.service.OvertimeDepartmentScopeTest.utcNow'");
        db.execute("CREATE TABLE dbo.roles(role_id bigint, role_code varchar(20))");
        db.execute("CREATE TABLE dbo.accounts(account_id bigint, employee_id bigint, role_id bigint, is_active int)");
        db.execute("CREATE TABLE dbo.permissions(permission_id bigint, permission_code varchar(50))");
        db.execute("CREATE TABLE dbo.role_permissions(role_id bigint, permission_id bigint)");
        db.execute("CREATE TABLE dbo.employee_assignments(employee_id bigint, department_id bigint, effective_from date, effective_to date)");
        db.execute("CREATE TABLE dbo.hr_department_scopes(hr_account_id bigint, department_id bigint, effective_from timestamp, effective_to timestamp, revoked_at timestamp)");
        db.execute("CREATE TABLE dbo.overtime_requests(employee_id bigint, work_date date)");
        db.update("INSERT INTO dbo.roles VALUES (1,'HR'),(2,'EMPLOYEE')");
        db.update("INSERT INTO dbo.accounts VALUES (1,101,1,1),(2,201,2,1)");
        db.update("INSERT INTO dbo.permissions VALUES (1,'OVERTIME_APPROVE')");
        db.update("INSERT INTO dbo.role_permissions VALUES (1,1)");
        db.update("INSERT INTO dbo.employee_assignments VALUES (201,10,DATE '2024-01-01',DATE '2025-02-01'),(201,20,DATE '2025-02-01',NULL)");
        db.update("INSERT INTO dbo.hr_department_scopes VALUES (1,10,TIMESTAMP '2026-01-01 00:00:00',NULL,NULL)");
        db.update("INSERT INTO dbo.overtime_requests VALUES (201,DATE '2025-01-31'),(201,DATE '2025-02-01')");
        permissions = new OvertimePermissionService(db);
    }

    @Test void currentGrantControlsHistoricalDepartmentInListAndDetail() {
        String sql = "SELECT COUNT(*) FROM dbo.overtime_requests o WHERE "
                + OvertimePermissionService.hrScopeExistsSql("o.employee_id", "o.work_date");
        assertEquals(1L, db.queryForObject(sql, Long.class, 1L));
        assertNotNull(permissions.requireView(hr, 201, LocalDate.of(2025, 1, 31)));
        assertThrows(ResponseStatusException.class, () -> permissions.requireView(hr, 201, LocalDate.of(2025, 2, 1)));
        db.update("UPDATE dbo.hr_department_scopes SET revoked_at=CURRENT_TIMESTAMP");
        assertEquals(0L, db.queryForObject(sql, Long.class, 1L));
        assertThrows(ResponseStatusException.class, () -> permissions.requireView(hr, 201, LocalDate.of(2025, 1, 31)));
    }

    @Test void viewingDoesNotRequireTargetToHaveAnActiveEmployeeAccount() {
        db.update("INSERT INTO dbo.employee_assignments VALUES (204,10,DATE '2024-01-01',NULL)");
        db.update("INSERT INTO dbo.overtime_requests VALUES (204,DATE '2025-01-31')");
        String sql = "SELECT COUNT(*) FROM dbo.overtime_requests o WHERE "
                + OvertimePermissionService.hrScopeExistsSql("o.employee_id", "o.work_date");
        assertEquals(2L, db.queryForObject(sql, Long.class, 1L));
        assertNotNull(permissions.requireView(hr, 204, LocalDate.of(2025, 1, 31)));
        assertFalse(OvertimeAuthorizationRules.canApprove("HR", null, 101L, 204, true));
    }

    @Test void hrMayViewButCannotProcessAnotherHrsOvertime() {
        db.update("INSERT INTO dbo.accounts VALUES (3,301,1,1)");
        db.update("INSERT INTO dbo.employee_assignments VALUES (301,10,DATE '2024-01-01',DATE '2025-02-01'),(301,20,DATE '2025-02-01',NULL)");
        db.update("INSERT INTO dbo.overtime_requests VALUES (301,DATE '2025-01-31'),(301,DATE '2025-02-01')");
        LocalDate inside = LocalDate.of(2025, 1, 31);
        LocalDate outside = LocalDate.of(2025, 2, 1);

        String sql = "SELECT COUNT(*) FROM dbo.overtime_requests o WHERE "
                + OvertimePermissionService.hrScopeExistsSql("o.employee_id", "o.work_date");
        assertEquals(2L, db.queryForObject(sql, Long.class, 1L));
        assertNotNull(permissions.requireView(hr, 301, inside));
        assertThrows(ResponseStatusException.class, () -> permissions.requireView(hr, 301, outside));
        assertThrows(ResponseStatusException.class, () -> permissions.requireApprove(hr, 301, inside));
        assertThrows(ResponseStatusException.class, () -> permissions.requireConfirm(hr, 301, inside, 9));
        assertThrows(ResponseStatusException.class, () -> permissions.requireOperate(hr, 301, inside));
        assertNotNull(permissions.requireApprove(hr, 201, inside));
        assertNotNull(permissions.requireConfirm(hr, 201, inside, 9));
    }
}
