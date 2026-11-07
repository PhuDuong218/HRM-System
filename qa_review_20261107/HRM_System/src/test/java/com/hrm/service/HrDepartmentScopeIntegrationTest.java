package com.hrm.service;

import com.hrm.dto.HrScopeDTOs.GrantRequest;
import com.hrm.dto.HrScopeDTOs.RevokeRequest;
import com.hrm.dto.HrScopeDTOs.UpdateRequest;
import com.hrm.repository.HrDepartmentScopeRepository;
import com.hrm.security.AuthenticatedAccount;
import java.time.LocalDateTime;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.web.server.ResponseStatusException;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Opt-in SQL Server integration tests; NEVER run on hrm_db.
 * No Spring Boot profile is loaded: application-test.properties currently
 * points to H2.
 * All writes occur in TransactionTemplate transactions marked rollback-only.
 * Does not test concurrent transactions; that requires separate explicit
 * authorization.
 */
class HrDepartmentScopeIntegrationTest {
    private static final AtomicBoolean HALTED = new AtomicBoolean(false);
    private static final long ADMIN_ID = 2L, HR_ID = 5L, DEPT_ID = 1L;
    private JdbcTemplate jdbc;
    private TransactionTemplate transactions;
    private HrDepartmentScopeService service;
    private final AuthenticatedAccount admin = new AuthenticatedAccount(ADMIN_ID, 1L, "admin.sample", "ADMIN");
    private long initialScopes, initialAudits;
    private int initialAdminActive;

    @BeforeEach
    void before() {
        assumeTrue("true".equalsIgnoreCase(System.getenv("HRM_IT_ENABLE")),
                "Integration tests disabled. Explicitly set HRM_IT_ENABLE=true to opt in.");
        assumeTrue(!HALTED.get(), "Stopped: an earlier verification failed.");
        String url = System.getenv("HRM_IT_JDBC_URL");
        assumeTrue(url != null && !url.isBlank(), "Supply HRM_IT_JDBC_URL for SQL Server hrm_test.");
        // Reject missing or ambiguous connection-string database properties BEFORE
        // connecting.
        String normalized = url.replaceAll("\\s+", "").toLowerCase(java.util.Locale.ROOT);
        assertTrue(normalized.startsWith("jdbc:sqlserver://"), "Only Microsoft SQL Server JDBC is permitted");
        assertTrue(normalized.matches(".*;databasename=hrm_test(?:;.*)?$"),
                "JDBC URL must explicitly specify databaseName=hrm_test");
        assertFalse(normalized.contains("databasename=hrm_db"), "Production database forbidden");

        DriverManagerDataSource ds = new DriverManagerDataSource();
        ds.setDriverClassName("com.microsoft.sqlserver.jdbc.SQLServerDriver");
        ds.setUrl(url);
        String user = System.getenv("HRM_IT_DB_USER");
        String password = System.getenv("HRM_IT_DB_PASSWORD");
        if (user != null && !user.isBlank())
            ds.setUsername(user);
        if (password != null)
            ds.setPassword(password);
        jdbc = new JdbcTemplate(ds);
        String actual = jdbc.queryForObject("SELECT DB_NAME()", String.class);
        assertEquals("hrm_test", actual, "ABORT: the live connection is not hrm_test");
        Integer tablePresent = jdbc.queryForObject(
                "SELECT CASE WHEN OBJECT_ID(N'dbo.hr_department_scopes', N'U') IS NULL THEN 0 ELSE 1 END",
                Integer.class);
        assertEquals(1, tablePresent, "Missing approved table in hrm_test");
        assertEquals("ADMIN", role(ADMIN_ID));
        assertEquals("HR", role(HR_ID));
        assertEquals("admin.sample", jdbc.queryForObject(
                "SELECT username FROM dbo.accounts WHERE account_id=?", String.class, ADMIN_ID));
        assertEquals("hr.sample", jdbc.queryForObject(
                "SELECT username FROM dbo.accounts WHERE account_id=?", String.class, HR_ID));
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM dbo.departments WHERE department_id=?",
                Integer.class, DEPT_ID));
        initialAdminActive = jdbc.queryForObject(
                "SELECT CAST(is_active AS int) FROM dbo.accounts WHERE account_id=?", Integer.class, ADMIN_ID);
        // This test suite never changes any pre-existing scope or audit entries.
        initialScopes = count("dbo.hr_department_scopes");
        initialAudits = count("dbo.audit_logs");
        transactions = new TransactionTemplate(new DataSourceTransactionManager(ds));
        service = new HrDepartmentScopeService(new HrDepartmentScopeRepository(jdbc));
    }

    private String role(long id) {
        return jdbc.queryForObject("SELECT r.role_code FROM dbo.accounts a " +
                "JOIN dbo.roles r ON a.role_id=r.role_id WHERE a.account_id=?", String.class, id);
    }

    private long count(String table) {
        // Hardcoded internal call sites only, never user input.
        return Objects.requireNonNull(jdbc.queryForObject("SELECT COUNT_BIG(*) FROM " + table, Long.class));
    }

    private void activateAdmin() {
        assertEquals(1, jdbc.update("UPDATE dbo.accounts SET is_active=1 WHERE account_id=?", ADMIN_ID));
    }

    private LocalDateTime now() {
        return Objects.requireNonNull(jdbc.queryForObject(
                "SELECT CAST(SYSUTCDATETIME() AS datetime2(3))", LocalDateTime.class));
    }

    @FunctionalInterface
    interface TestStep {
        void execute();
    }

    private void rolledBack(TestStep body) {
        try {
            transactions.execute(status -> {
                // Guard again inside the SAME JDBC transaction as service and update.
                assertEquals("hrm_test", jdbc.queryForObject("SELECT DB_NAME()", String.class));
                try {
                    body.execute();
                } finally {
                    status.setRollbackOnly();
                }
                return null;
            });
        } finally {
            verifyUnchanged();
        }
    }

    private void verifyUnchanged() {
        try {
            assertEquals("hrm_test", jdbc.queryForObject("SELECT DB_NAME()", String.class));
            assertEquals(initialAdminActive, jdbc.queryForObject(
                    "SELECT CAST(is_active AS int) FROM dbo.accounts WHERE account_id=?",
                    Integer.class, ADMIN_ID));
            assertEquals(initialScopes, count("dbo.hr_department_scopes"), "Scope row count changed");
            assertEquals(initialAudits, count("dbo.audit_logs"), "Audit row count changed");
        } catch (Throwable failure) {
            HALTED.set(true);
            throw new AssertionError("STOP: database state could not be verified after test; do not continue", failure);
        }
    }

    @AfterEach
    void after() {
        if (jdbc != null && !HALTED.get())
            verifyUnchanged();
    }

    private long grant(LocalDateTime from, LocalDateTime to) {
        return service.grant(admin, new GrantRequest(HR_ID, DEPT_ID, from, to, "Integration test"))
                .scopeId();
    }

    private void auditCount(String action, long scope, int expected) {
        Integer n = jdbc.queryForObject(
                "SELECT COUNT(*) FROM dbo.audit_logs WHERE target_table='hr_department_scopes' " +
                        "AND target_record_key=? AND action=?",
                Integer.class, Long.toString(scope), action);
        assertEquals(expected, n);
    }

    @Test
    void it01_inactiveAdminDenied() {
        rolledBack(() -> {
            assertEquals(0, initialAdminActive, "Fixture requires the sample ADMIN to be inactive");
            assertThrows(ResponseStatusException.class,
                    () -> grant(now().plusDays(2), null));
        });
    }

    @Test
    void it02_adminGrantAndAudit() {
        rolledBack(() -> {
            activateAdmin();
            long id = grant(now().plusDays(2), null);
            assertNotNull(service.all(admin).stream().filter(s -> s.scopeId() == id).findFirst().orElse(null));
            auditCount("HR_SCOPE_GRANTED", id, 1);
        });
    }

    @Test
    void it03_nonHrRecipientDenied() {
        rolledBack(() -> {
            activateAdmin();
            assertThrows(ResponseStatusException.class, () -> service.grant(admin,
                    new GrantRequest(1L, DEPT_ID, now().plusDays(2), null, "Test")));
        });
    }

    @Test
    void it04_unknownDepartmentDenied() {
        rolledBack(() -> {
            activateAdmin();
            assertThrows(ResponseStatusException.class, () -> service.grant(admin,
                    new GrantRequest(HR_ID, Long.MAX_VALUE, now().plusDays(2), null, "Test")));
        });
    }

    @Test
    void it05_futureGrantNotActive() {
        rolledBack(() -> {
            activateAdmin();
            grant(now().plusDays(3), null);
            assertFalse(new HrDepartmentScopeRepository(jdbc).activeScope(HR_ID, DEPT_ID));
        });
    }

    @Test
    void it06_overlapDenied() {
        rolledBack(() -> {
            activateAdmin();
            LocalDateTime from = now().plusDays(3);
            grant(from, from.plusDays(20));
            assertThrows(ResponseStatusException.class,
                    () -> grant(from.plusDays(1), from.plusDays(10)));
        });
    }

    @Test
    void it07_changeFutureStartAudited() {
        rolledBack(() -> {
            activateAdmin();
            LocalDateTime from = now().plusDays(4);
            long id = grant(from, null);
            var changed = service.update(admin, id,
                    new UpdateRequest(from.plusDays(1), null, "Moved future start"));
            assertEquals(from.plusDays(1), changed.effectiveFrom());
            auditCount("HR_SCOPE_UPDATED", id, 1);
        });
    }

    @Test
    void it08_activeStartCannotBeChanged() {
        rolledBack(() -> {
            activateAdmin();
            LocalDateTime from = now().minusDays(1);
            long id = grant(from, null);
            assertThrows(ResponseStatusException.class, () -> service.update(admin, id,
                    new UpdateRequest(from.plusDays(2), null, "Must reject")));
        });
    }

    @Test
    void it09_extendActiveEnd() {
        rolledBack(() -> {
            activateAdmin();
            LocalDateTime from = now().minusDays(1);
            long id = grant(from, now().plusDays(3));
            LocalDateTime extended = now().plusDays(5);
            assertEquals(extended, service.update(admin, id,
                    new UpdateRequest(from, extended, "Extend")).effectiveTo());
        });
    }

    @Test
    void it10_revokeImmediatelyAndBlockReactivation() {
        rolledBack(() -> {
            activateAdmin();
            long id = grant(now().minusDays(1), null);
            assertTrue(new HrDepartmentScopeRepository(jdbc).activeScope(HR_ID, DEPT_ID));
            service.revoke(admin, id, new RevokeRequest("Revoke test"));
            assertFalse(new HrDepartmentScopeRepository(jdbc).activeScope(HR_ID, DEPT_ID));
            assertThrows(ResponseStatusException.class, () -> service.update(admin, id,
                    new UpdateRequest(now().minusDays(1), null, "Reactivate")));
            auditCount("HR_SCOPE_REVOKED", id, 1);
        });
    }

    @Test
    void it11_auditBeforeAndAfterAreValidJson() {
        rolledBack(() -> {
            activateAdmin();
            long id = grant(now().plusDays(2), null);
            Integer n = jdbc.queryForObject("""
                    SELECT COUNT(*) FROM dbo.audit_logs
                    WHERE target_table='hr_department_scopes' AND target_record_key=?
                      AND action='HR_SCOPE_GRANTED'
                      AND before_data_json IS NULL AND ISJSON(after_data_json)=1
                      AND actor_account_id=?
                    """, Integer.class, Long.toString(id), ADMIN_ID);
            assertEquals(1, n);
        });
    }

    @Test
    void it12_forcedFailureRollsBackScopeAndAudit() {
        rolledBack(() -> {
            activateAdmin();
            long id = grant(now().plusDays(2), null);
            // Simulate a failure after grant+audit, proving outer transaction rollback.
            assertThrows(IllegalStateException.class, () -> {
                auditCount("HR_SCOPE_GRANTED", id, 1);
                throw new IllegalStateException("Simulated downstream failure");
            });
        });
    }
}
