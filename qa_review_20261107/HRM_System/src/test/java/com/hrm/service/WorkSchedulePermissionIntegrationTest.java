package com.hrm.service;

import com.hrm.dto.WorkTimeDTOs.ScheduleInput;
import com.hrm.dto.WorkTimeDTOs.ScheduleView;
import com.hrm.security.AuthenticatedAccount;
import java.time.LocalDateTime;
import java.time.LocalDate;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.support.JdbcTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Explicit opt-in SQL Server integration tests for schedule write RBAC.
 * NEVER targets hrm_db. No Spring Boot application context or application
 * properties.
 * All fixtures and writes occur in rollback-only JDBC transactions on hrm_test.
 * Deliberately does not test concurrency or HTTP/JWT processing.
 */
class WorkSchedulePermissionIntegrationTest {
    private static final AtomicBoolean HALT = new AtomicBoolean(false);
    private static final String[] TRACKED = {
            "dbo.work_schedules", "dbo.hr_department_scopes", "dbo.employee_assignments",
            "dbo.accounts", "dbo.employees", "dbo.audit_logs"
    };
    private JdbcTemplate jdbc;
    private TransactionTemplate tx;
    private WorkScheduleService schedules;
    private long[] beforeCounts;
    private long adminId, hrId, departmentId, positionId, employeeRoleId;
    private long adminEmployeeId, hrEmployeeId;

    @BeforeEach
    void setup() {
        assumeTrue("true".equalsIgnoreCase(System.getenv("HRM_IT_ENABLE")),
                "Opt-in required: HRM_IT_ENABLE=true");
        assumeTrue(!HALT.get(), "Previous test could not verify rollback; halted");
        String url = System.getenv("HRM_IT_JDBC_URL");
        assumeTrue(url != null && !url.isBlank(), "HRM_IT_JDBC_URL is required");
        String normalized = url.replaceAll("\\s+", "").toLowerCase(Locale.ROOT);
        assertTrue(normalized.startsWith("jdbc:sqlserver://"), "Only Microsoft SQL Server JDBC allowed");
        assertTrue(normalized.matches(".*;databasename=hrm_test(?:;.*)?$"),
                "Explicit databaseName=hrm_test required; refusing any other database");
        assertFalse(normalized.contains("databasename=hrm_db"));
        // Explicit credentials are required; never silently fall back to Windows or app
        // defaults.
        String user = System.getenv("HRM_IT_DB_USER");
        String password = System.getenv("HRM_IT_DB_PASSWORD");
        assumeTrue(user != null && !user.isBlank() && password != null && !password.isBlank(),
                "Explicit HRM_IT_DB_USER and HRM_IT_DB_PASSWORD are required");
        DriverManagerDataSource ds = new DriverManagerDataSource();
        ds.setDriverClassName("com.microsoft.sqlserver.jdbc.SQLServerDriver");
        ds.setUrl(url);
        ds.setUsername(user);
        ds.setPassword(password);
        jdbc = new JdbcTemplate(ds);
        assertEquals("hrm_test", jdbc.queryForObject("SELECT DB_NAME()", String.class),
                "STOP: connected database is not hrm_test");
        for (String table : TRACKED) {
            assertEquals(1, jdbc.queryForObject(
                    "SELECT CASE WHEN OBJECT_ID(N'" + table + "', N'U') IS NULL THEN 0 ELSE 1 END", Integer.class),
                    "Required table absent: " + table);
        }
        adminId = requireAccount("ADMIN", "admin.sample");
        hrId = requireAccount("HR", "hr.sample");
        adminEmployeeId = requireEmployeeForAccount(adminId);
        hrEmployeeId = requireEmployeeForAccount(hrId);
        departmentId = requireId("SELECT TOP (1) department_id FROM dbo.departments ORDER BY department_id");
        positionId = requireId("SELECT TOP (1) position_id FROM dbo.positions ORDER BY position_id");
        employeeRoleId = requireId("SELECT role_id FROM dbo.roles WHERE role_code='EMPLOYEE'");
        beforeCounts = counts();
        // Same manager type/name behavior as JdbcTransactionConfig; one
        // datasource/connection per transaction.
        tx = new TransactionTemplate(new JdbcTransactionManager(ds));
        schedules = new WorkScheduleService(jdbc, new ScheduleWritePermissionService(jdbc));
    }

    private long requireAccount(String role, String username) {
        var ids = jdbc.query("SELECT a.account_id FROM dbo.accounts a JOIN dbo.roles r ON r.role_id=a.role_id " +
                "WHERE a.username=? AND r.role_code=?", (rs, i) -> rs.getLong(1), username, role);
        assertEquals(1, ids.size(), "Required test fixture account missing: " + username);
        return ids.get(0);
    }

    private long requireEmployeeForAccount(long accountId) {
        return Objects.requireNonNull(jdbc.queryForObject(
                "SELECT employee_id FROM dbo.accounts WHERE account_id=?", Long.class, accountId));
    }

    private long requireId(String sql) {
        var ids = jdbc.query(sql, (rs, i) -> rs.getLong(1));
        assertFalse(ids.isEmpty(), "Required existing reference data is absent; stopping without writes");
        return ids.get(0);
    }

    private long[] counts() {
        assertEquals("hrm_test", jdbc.queryForObject("SELECT DB_NAME()", String.class));
        long[] result = new long[TRACKED.length];
        for (int i = 0; i < TRACKED.length; i++) {
            // Only hardcoded table identifiers in TRACKED; not user input.
            result[i] = Objects.requireNonNull(jdbc.queryForObject(
                    "SELECT COUNT_BIG(*) FROM " + TRACKED[i], Long.class));
        }
        return result;
    }

    private void verifyRollback() {
        try {
            assertArrayEquals(beforeCounts, counts(), "STOP: tracked table counts differ from baseline");
        } catch (Throwable t) {
            HALT.set(true);
            throw new AssertionError("STOP: rollback verification failed; do not run further tests", t);
        }
    }

    @AfterEach
    void after() {
        if (jdbc != null && beforeCounts != null && !HALT.get())
            verifyRollback();
    }

    @FunctionalInterface
    private interface Case {
        void run(Fixture f);
    }

    private void rolledBack(Case body) {
        try {
            tx.execute(status -> {
                assertEquals("hrm_test", jdbc.queryForObject("SELECT DB_NAME()", String.class));
                try {
                    Fixture f = createFixture();
                    body.run(f);
                } finally {
                    status.setRollbackOnly();
                }
                return null;
            });
        } finally {
            verifyRollback();
        }
    }

    private record Fixture(long employeeId, long scopeId, AuthenticatedAccount admin,
            AuthenticatedAccount hr, LocalDateTime start) {
    }

    private Fixture createFixture() {
        // The two pre-existing sample accounts are changed ONLY within the rollback
        // transaction.
        assertEquals(1, jdbc.update("UPDATE dbo.accounts SET is_active=1 WHERE account_id=?", adminId));
        assertEquals(1, jdbc.update("UPDATE dbo.accounts SET is_active=1 WHERE account_id=?", hrId));
        String unique = UUID.randomUUID().toString().replace("-", "");
        Long employee = jdbc.queryForObject("""
                INSERT INTO dbo.employees(employee_code,full_name,hire_date,employment_status)
                OUTPUT INSERTED.employee_id VALUES (?,N'RBAC rollback test',?,'DANG_LAM')
                """, Long.class, "RBACIT" + unique.substring(0, 20), LocalDate.of(2020, 1, 1));
        assertNotNull(employee);
        Long account = jdbc.queryForObject("""
                INSERT INTO dbo.accounts(employee_id,role_id,username,password_hash,is_active)
                OUTPUT INSERTED.account_id VALUES (?,?,?,?,1)
                """, Long.class, employee, employeeRoleId, "rbacit_" + unique,
                "x".repeat(60));
        assertNotNull(account);
        jdbc.update("""
                INSERT INTO dbo.employee_assignments(employee_id,department_id,position_id,effective_from)
                VALUES (?,?,?,?)
                """, employee, departmentId, positionId, LocalDate.of(2020, 1, 1));
        // Immediate scope (not future) is available only within the rollback
        // transaction.
        Long scope = jdbc.queryForObject("""
                INSERT INTO dbo.hr_department_scopes(hr_account_id,department_id,effective_from,created_by)
                OUTPUT INSERTED.scope_id VALUES (?,?,DATEADD(day,-1,SYSUTCDATETIME()),?)
                """, Long.class, hrId, departmentId, adminId);
        assertNotNull(scope);
        LocalDateTime start = LocalDate.now(java.time.ZoneId.of("Asia/Ho_Chi_Minh"))
                .plusDays(60).atTime(9, 0);
        return new Fixture(employee, scope,
                new AuthenticatedAccount(adminId, adminEmployeeId, "admin.sample", "ADMIN"),
                new AuthenticatedAccount(hrId, hrEmployeeId, "hr.sample", "HR"), start);
    }

    private ScheduleInput input(long employeeId, LocalDateTime start) {
        return new ScheduleInput(employeeId, null, start, start.plusHours(2), 0, "RBAC integration rollback test");
    }

    private static void denied(int expected, org.junit.jupiter.api.function.Executable action) {
        ResponseStatusException ex = assertThrows(ResponseStatusException.class, action);
        assertEquals(expected, ex.getStatusCode().value());
    }

    @Test
    void it01_adminCanCreateScheduleForEmployee() {
        rolledBack(f -> {
            ScheduleView result = schedules.create(f.admin(), input(f.employeeId(), f.start()));
            assertEquals(f.employeeId(), result.employeeId());
            assertEquals(1L, jdbc.queryForObject(
                    "SELECT COUNT_BIG(*) FROM dbo.work_schedules WHERE schedule_id=?", Long.class,
                    result.scheduleId()));
        });
    }

    @Test
    void it02_hrCanCreateWithinCurrentScope() {
        rolledBack(f -> assertEquals(f.employeeId(),
                schedules.create(f.hr(), input(f.employeeId(), f.start())).employeeId()));
    }

    @Test
    void it03_hrDeniedAfterRevocation() {
        rolledBack(f -> {
            jdbc.update("UPDATE dbo.hr_department_scopes SET revoked_at=SYSUTCDATETIME(),revoked_by=? WHERE scope_id=?",
                    adminId, f.scopeId());
            denied(403, () -> schedules.create(f.hr(), input(f.employeeId(), f.start())));
        });
    }

    @Test
    void it04_hrDeniedWhenScopeNotYetEffective() {
        rolledBack(f -> {
            jdbc.update(
                    "UPDATE dbo.hr_department_scopes SET effective_from=DATEADD(day,1,SYSUTCDATETIME()) WHERE scope_id=?",
                    f.scopeId());
            denied(403, () -> schedules.create(f.hr(), input(f.employeeId(), f.start())));
        });
    }

    @Test
    void it05_hrDeniedWhenTargetAccountInactive() {
        rolledBack(f -> {
            jdbc.update("UPDATE dbo.accounts SET is_active=0 WHERE employee_id=?", f.employeeId());
            denied(403, () -> schedules.create(f.hr(), input(f.employeeId(), f.start())));
        });
    }

    @Test
    void it06_hrDeniedOutsideAssignedDepartment() {
        rolledBack(f -> {
            // Make target assignment end before the new schedule date (exclusive end).
            jdbc.update("UPDATE dbo.employee_assignments SET effective_to=? WHERE employee_id=?",
                    f.start().toLocalDate(), f.employeeId());
            denied(403, () -> schedules.create(f.hr(), input(f.employeeId(), f.start())));
        });
    }

    @Test
    void it07_adminDeniedForResignedEmployee() {
        rolledBack(f -> {
            jdbc.update("UPDATE dbo.employees SET employment_status='DA_NGHI',termination_date=? WHERE employee_id=?",
                    LocalDate.now().minusDays(1), f.employeeId());
            denied(403, () -> schedules.create(f.admin(), input(f.employeeId(), f.start())));
        });
    }

    @Test
    void it08_hrCanUpdateFutureScheduleWithinScope() {
        rolledBack(f -> {
            ScheduleView created = schedules.create(f.admin(), input(f.employeeId(), f.start()));
            ScheduleView updated = schedules.update(f.hr(), created.scheduleId(),
                    input(f.employeeId(), f.start().plusHours(1)));
            assertEquals(f.start().plusHours(1), updated.plannedStart());
        });
    }

    @Test
    void it09_hrDeniedUpdateAfterScopeRevoked() {
        rolledBack(f -> {
            ScheduleView created = schedules.create(f.admin(), input(f.employeeId(), f.start()));
            jdbc.update("UPDATE dbo.hr_department_scopes SET revoked_at=SYSUTCDATETIME(),revoked_by=? WHERE scope_id=?",
                    adminId, f.scopeId());
            denied(403, () -> schedules.update(f.hr(), created.scheduleId(),
                    input(f.employeeId(), f.start().plusHours(1))));
        });
    }

    @Test
    void it10_overlapDenied() {
        rolledBack(f -> {
            schedules.create(f.admin(), input(f.employeeId(), f.start()));
            denied(409, () -> schedules.create(f.admin(), input(f.employeeId(), f.start().plusMinutes(30))));
        });
    }

    @Test
    void it11_ownerChangeDenied() {
        rolledBack(f -> {
            ScheduleView created = schedules.create(f.admin(), input(f.employeeId(), f.start()));
            denied(409, () -> schedules.update(f.admin(), created.scheduleId(),
                    input(hrEmployeeId, f.start().plusDays(1))));
        });
    }

    @Test
    void it12_nonAdminNonHrCannotCreate() {
        rolledBack(f -> {
            Long employeeAccountId = jdbc.queryForObject(
                    "SELECT account_id FROM dbo.accounts WHERE employee_id=?", Long.class, f.employeeId());
            denied(403, () -> schedules.create(
                    new AuthenticatedAccount(employeeAccountId, f.employeeId(), "test.employee", "EMPLOYEE"),
                    input(f.employeeId(), f.start())));
        });
    }
}
