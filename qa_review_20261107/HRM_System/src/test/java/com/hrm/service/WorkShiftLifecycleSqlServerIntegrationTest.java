package com.hrm.service;

import com.hrm.dto.WorkTimeDTOs.ScheduleInput;
import com.hrm.dto.WorkTimeDTOs.ShiftInput;
import com.hrm.security.AuthenticatedAccount;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.Locale;
import java.util.UUID;
import javax.sql.DataSource;
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
 * REVIEW ONLY: no test executes unless explicitly opted in. No application
 * context,
 * application-local.properties, shared accounts, or hrm_db connection is used.
 * Each fixture and all service writes are inside ONE rollback-only SQL
 * transaction.
 * This class deliberately does NOT claim to test committed concurrency, HTTP or
 * JWT.
 */
class WorkShiftLifecycleSqlServerIntegrationTest {
    private JdbcTemplate db;
    private TransactionTemplate transaction;
    private WorkShiftService shifts;
    private WorkScheduleService schedules;

    @BeforeEach
    void prepare() {
        assumeTrue("true".equalsIgnoreCase(System.getenv("HRM_T7_IT_ENABLE")),
                "Disabled by default: requires separate approval to run");
        String url = System.getenv("HRM_T7_IT_JDBC_URL");
        assumeTrue(url != null && !url.isBlank(), "Explicit test JDBC URL required");
        String n = url.replaceAll("\\s+", "").toLowerCase(Locale.ROOT);
        assertTrue(n.startsWith("jdbc:sqlserver://"), "SQL Server only");
        assertTrue(n.matches(".*;databasename=hrm_test(?:;.*)?$"), "Only explicit hrm_test is allowed");
        assertFalse(n.contains("databasename=hrm_db"));
        String user = System.getenv("HRM_T7_IT_DB_USER");
        String pass = System.getenv("HRM_T7_IT_DB_PASSWORD");
        assumeTrue(user != null && !user.isBlank() && pass != null && !pass.isBlank(),
                "Explicit SQL login credentials required; no fallback to local properties");
        DriverManagerDataSource ds = new DriverManagerDataSource();
        ds.setDriverClassName("com.microsoft.sqlserver.jdbc.SQLServerDriver");
        ds.setUrl(url);
        ds.setUsername(user);
        ds.setPassword(pass);
        db = new JdbcTemplate(ds);
        assertEquals("hrm_test", db.queryForObject("SELECT DB_NAME()", String.class));
        transaction = new TransactionTemplate(new JdbcTransactionManager((DataSource) ds));
        shifts = new WorkShiftService(db, new WorkShiftPermissionService(db));
        schedules = new WorkScheduleService(db, new ScheduleWritePermissionService(db));
    }

    private record Fixture(AuthenticatedAccount admin, long employeeId, String tag, LocalDateTime future) {
    }

    private Fixture fixture() {
        assertEquals("hrm_test", db.queryForObject("SELECT DB_NAME()", String.class));
        String tag = "T7_IT_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        Long roleId = db.queryForObject("SELECT role_id FROM dbo.roles WHERE role_code='ADMIN'", Long.class);
        assertNotNull(roleId, "ADMIN role is required");
        Long employeeId = db.queryForObject("""
                INSERT dbo.employees(employee_code,full_name,hire_date,employment_status,created_at)
                OUTPUT INSERTED.employee_id VALUES (?,N'Task7 isolated test',?,'DANG_LAM',SYSUTCDATETIME())
                """, Long.class, tag, LocalDate.of(2024, 1, 1));
        assertNotNull(employeeId);
        String username = "t7_it_" + tag.substring(6).toLowerCase(Locale.ROOT);
        Long accountId = db.queryForObject("""
                INSERT dbo.accounts(employee_id,role_id,username,password_hash,is_active,token_version,created_at)
                OUTPUT INSERTED.account_id VALUES (?,?,?, ?,1,0,SYSUTCDATETIME())
                """, Long.class, employeeId, roleId, username, "!".repeat(60));
        assertNotNull(accountId);
        LocalDateTime future = LocalDate.now(ZoneId.of("Asia/Ho_Chi_Minh"))
                .plusDays(180).atTime(9, 0);
        return new Fixture(new AuthenticatedAccount(accountId, employeeId, username, "ADMIN"),
                employeeId, tag, future);
    }

    private void rolledBack(java.util.function.Consumer<Fixture> assertion) {
        transaction.execute(status -> {
            assertEquals("hrm_test", db.queryForObject("SELECT DB_NAME()", String.class));
            // Explicit rollback even if assertion fails; TransactionTemplate also rolls
            // back on unchecked exceptions.
            try {
                assertion.accept(fixture());
            } finally {
                status.setRollbackOnly();
            }
            return null;
        });
    }

    private ShiftInput shift(String code, LocalTime start, LocalTime end, int offset) {
        return new ShiftInput(code, "Task7 isolated test", start, end, offset, 0, true);
    }

    private ShiftInput normal(String code) {
        return shift(code, LocalTime.of(9, 0), LocalTime.of(17, 0), 0);
    }

    private long refs(long shiftId) {
        Long n = db.queryForObject("SELECT COUNT_BIG(*) FROM dbo.work_schedules WHERE shift_id=?", Long.class, shiftId);
        return n == null ? -1 : n;
    }

    private long present(long shiftId) {
        Long n = db.queryForObject("SELECT COUNT_BIG(*) FROM dbo.work_shifts WHERE shift_id=?", Long.class, shiftId);
        return n == null ? -1 : n;
    }

    private long schedule(Fixture f, long shiftId) {
        return schedules.create(f.admin(), new ScheduleInput(f.employeeId(), shiftId,
                f.future(), f.future().plusHours(8), 0, "Task7 isolated integration")).scheduleId();
    }

    private static void conflict(org.junit.jupiter.api.function.Executable action) {
        ResponseStatusException ex = assertThrows(ResponseStatusException.class, action);
        assertEquals(HttpStatus.CONFLICT, ex.getStatusCode());
    }

    @Test
    void durationExactly24HoursAndOver24Rejected() {
        rolledBack(f -> {
            var ok = shifts.create(f.admin(), shift(f.tag() + "_24", LocalTime.of(8, 0), LocalTime.of(8, 0), 1));
            assertEquals(1, present(ok.shiftId()));
            assertThrows(IllegalArgumentException.class, () -> shifts.create(f.admin(),
                    shift(f.tag() + "_25", LocalTime.of(8, 0), LocalTime.of(8, 1), 1)));
        });
    }

    @Test
    void unreferencedShiftIsDeactivated() {
        rolledBack(f -> {
            long id = shifts.create(f.admin(), normal(f.tag() + "_DEL")).shiftId();
            assertEquals(0, refs(id));
            shifts.deleteOrDeactivate(f.admin(), id);
            assertEquals(1, present(id));
            assertFalse(shifts.get(id).active());
        });
    }

    @Test
    void referencedShiftBecomesInactiveAndTimeCannotChange() {
        rolledBack(f -> {
            long id = shifts.create(f.admin(), normal(f.tag() + "_REF")).shiftId();
            long scheduleId = schedule(f, id);
            assertEquals(1, refs(id));
            conflict(() -> shifts.update(f.admin(), id,
                    shift(f.tag() + "_REF", LocalTime.of(10, 0), LocalTime.of(18, 0), 0)));
            shifts.deleteOrDeactivate(f.admin(), id);
            assertFalse(shifts.get(id).active());
            assertEquals(id, schedules.get(scheduleId).shiftId());
        });
    }

    @Test
    void inactiveShiftCannotBeAssignedToNewSchedule() {
        rolledBack(f -> {
            long used = shifts.create(f.admin(), normal(f.tag() + "_USED")).shiftId();
            schedule(f, used);
            shifts.deleteOrDeactivate(f.admin(), used);
            // Change planned time so overlap checking is not the reason for rejection.
            conflict(() -> schedules.create(f.admin(), new ScheduleInput(f.employeeId(), used,
                    f.future().plusDays(1), f.future().plusDays(1).plusHours(8), 0, "new")));
        });
    }

    @Test
    void unchangedInactiveShiftIsAllowedForUnstartedSchedule() {
        rolledBack(f -> {
            long id = shifts.create(f.admin(), normal(f.tag() + "_OLD")).shiftId();
            long sid = schedule(f, id);
            shifts.deleteOrDeactivate(f.admin(), id);
            var old = schedules.get(sid);
            var updated = schedules.update(f.admin(), sid, new ScheduleInput(f.employeeId(), id,
                    old.plannedStart(), old.plannedEnd(), old.plannedBreakMinutes(), "revised note"));
            assertEquals(id, updated.shiftId());
            assertEquals("revised note", updated.note());
        });
    }

    @Test
    void cannotSwitchScheduleToAnotherInactiveShift() {
        rolledBack(f -> {
            long origin = shifts.create(f.admin(), normal(f.tag() + "_SRC")).shiftId();
            long dest = shifts.create(f.admin(), normal(f.tag() + "_DST")).shiftId();
            long sid = schedule(f, origin);
            // Create a future reference to dest via a DIFFERENT employee to avoid schedule
            // overlap.
            // Instead use a second future schedule for the same employee, at another day.
            schedules.create(f.admin(), new ScheduleInput(f.employeeId(), dest,
                    f.future().plusDays(2), f.future().plusDays(2).plusHours(8), 0, "dest ref"));
            shifts.deleteOrDeactivate(f.admin(), dest);
            var old = schedules.get(sid);
            conflict(() -> schedules.update(f.admin(), sid,
                    new ScheduleInput(f.employeeId(), dest, old.plannedStart(), old.plannedEnd(), 0, "bad")));
        });
    }
}
