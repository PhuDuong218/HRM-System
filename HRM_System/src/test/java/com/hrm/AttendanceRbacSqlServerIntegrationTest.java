package com.hrm;

import static org.junit.jupiter.api.Assertions.*;

import java.sql.DriverManager;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.support.JdbcTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

import com.hrm.dto.AttendanceDTOs.AttendanceView;
import com.hrm.service.AttendancePermissionService;
import com.hrm.service.AttendanceService;

/**
 * REVIEW-ONLY / OPT-IN. This class is intentionally skipped during ordinary
 * Maven tests.
 * Execution requires separate, explicit SQL-write authorization. When
 * separately authorized, all fixture INSERTs
 * and business UPDATEs share one SQL Server connection/transaction, marked
 * rollback-only
 * BEFORE any DML. No DELETE or COMMIT is issued.
 *
 * Assumptions checked before fixture creation: exact database, required
 * columns,
 * roles/permissions, active referenced department/position, no conflicting
 * prefix.
 * Never run against the shared hrm_db database.
 */
class AttendanceRbacSqlServerIntegrationTest {
    private static final Pattern TEST_URL = Pattern.compile(
            "(?is)(?:^|;)\\s*databaseName\\s*=\\s*hrm_test\\s*(?:;|$)");
    private static final String PREFIX = "T8_IT04_";

    private static boolean enabled() {
        return "true".equalsIgnoreCase(System.getenv("HRM_T8_IT_ENABLE"))
                && "true".equalsIgnoreCase(System.getenv("HRM_T8_IT_WRITE_APPROVED"))
                && "true".equalsIgnoreCase(System.getenv("HRM_T8_IT_ROLLBACK_FIXTURE_APPROVED"))
                && "true".equalsIgnoreCase(System.getenv("HRM_T8_IT04_RBAC_APPROVED"));
    }

    private static String env(String name) {
        String value = System.getenv(name);
        Assumptions.assumeTrue(value != null && !value.isBlank(), name + " required");
        return value;
    }

    private static long count(JdbcTemplate db, String sql, Object... args) {
        Long n = db.queryForObject(sql, Long.class, args);
        assertNotNull(n);
        return n;
    }

    private static void requireColumns(JdbcTemplate db, String table, String... required) {
        Set<String> columns = new HashSet<>(db.queryForList("""
                SELECT COLUMN_NAME FROM INFORMATION_SCHEMA.COLUMNS
                WHERE TABLE_SCHEMA='dbo' AND TABLE_NAME=?
                """, String.class, table));
        assertTrue(columns.containsAll(Arrays.asList(required)),
                "Schema mismatch in dbo." + table + "; STOP before any fixture INSERT");
    }

    /**
     * Collect ALL fixture-column mismatches before any INSERT instead of failing on
     * the first missing NOT NULL field. This stays fail-closed: only nullable,
     * defaulted, identity, computed, or rowversion columns can be omitted.
     */
    private static void requireInsertShape(JdbcTemplate db, String table,
            String[] suppliedColumns, String[] nullableOrDefaultedExpected,
            List<String> problems) {
        Set<String> supplied = new HashSet<>(Arrays.asList(suppliedColumns));
        Set<String> expected = new HashSet<>(Arrays.asList(nullableOrDefaultedExpected));
        var cols = db.queryForList("""
                SELECT COLUMN_NAME, DATA_TYPE, IS_NULLABLE, COLUMN_DEFAULT,
                       COLUMNPROPERTY(OBJECT_ID('dbo.' + TABLE_NAME), COLUMN_NAME, 'IsIdentity') AS is_identity,
                       COLUMNPROPERTY(OBJECT_ID('dbo.' + TABLE_NAME), COLUMN_NAME, 'IsComputed') AS is_computed
                FROM INFORMATION_SCHEMA.COLUMNS
                WHERE TABLE_SCHEMA='dbo' AND TABLE_NAME=?
                """, table);
        if (cols.isEmpty()) {
            problems.add("Missing or invisible table dbo." + table);
            return;
        }
        Set<String> actual = new HashSet<>();
        for (var c : cols) {
            String col = (String) c.get("COLUMN_NAME");
            actual.add(col);
            String dataType = String.valueOf(c.get("DATA_TYPE"));
            boolean rowVersion = "timestamp".equalsIgnoreCase(dataType)
                    || "rowversion".equalsIgnoreCase(dataType);
            boolean generated = NumberIsOne(c.get("is_identity"))
                    || NumberIsOne(c.get("is_computed")) || rowVersion;
            boolean optional = "YES".equals(c.get("IS_NULLABLE"))
                    || c.get("COLUMN_DEFAULT") != null || generated;
            if (!supplied.contains(col) && !optional) {
                problems.add("Unmapped required column dbo." + table + "." + col);
            }
            if (expected.contains(col) && !optional) {
                problems.add("Expected optional column now required dbo." + table + "." + col);
            }
            if (supplied.contains(col) && generated) {
                problems.add("Fixture tries to provide SQL-generated column dbo." + table + "." + col);
            }
        }
        for (String column : supplied) {
            if (!actual.contains(column)) {
                problems.add("INSERT column missing from dbo." + table + ": " + column);
            }
        }
        for (String column : expected) {
            if (!actual.contains(column)) {
                problems.add("Expected column missing from dbo." + table + ": " + column);
            }
        }
    }

    private static boolean NumberIsOne(Object n) {
        return n instanceof Number && ((Number) n).intValue() == 1;
    }

    /**
     * Validate an exact FK using visible sys metadata first, then SQL Server's
     * sp_fkeys metadata procedure when catalog visibility is restricted.
     * Both paths are read-only. A mismatch or inconclusive result fails closed
     * before any fixture INSERT.
     */
    private static void requireForeignKey(JdbcTemplate db, String childTable,
            String childColumn, String parentTable, String parentColumn) {
        long matches = count(db, """
                SELECT COUNT_BIG(*) FROM sys.foreign_key_columns fkc
                JOIN sys.tables child ON child.object_id=fkc.parent_object_id
                JOIN sys.schemas cs ON cs.schema_id=child.schema_id
                JOIN sys.columns cc ON cc.object_id=child.object_id AND cc.column_id=fkc.parent_column_id
                JOIN sys.tables parent ON parent.object_id=fkc.referenced_object_id
                JOIN sys.schemas ps ON ps.schema_id=parent.schema_id
                JOIN sys.columns pc ON pc.object_id=parent.object_id AND pc.column_id=fkc.referenced_column_id
                WHERE cs.name='dbo' AND ps.name='dbo'
                  AND child.name=? AND cc.name=? AND parent.name=? AND pc.name=?
                """, childTable, childColumn, parentTable, parentColumn);
        assertTrue(matches <= 1L, "HARD STOP: ambiguous FK metadata for dbo."
                + childTable + "." + childColumn);
        if (matches == 1L) {
            return;
        }

        // The SQL Server account may not see some sys.foreign_key_columns rows.
        // sp_fkeys was verified read-only on hrm_test; match BOTH table owners,
        // both tables and both columns instead of merely accepting any result.
        var imported = db.query(
                "EXEC sp_fkeys @fktable_name = ?, @fktable_owner = N'dbo'",
                (rs, rowNum) -> new String[] {
                        rs.getString("FKTABLE_OWNER"), rs.getString("FKTABLE_NAME"),
                        rs.getString("FKCOLUMN_NAME"), rs.getString("PKTABLE_OWNER"),
                        rs.getString("PKTABLE_NAME"), rs.getString("PKCOLUMN_NAME")
                }, childTable);
        long fallbackMatches = imported.stream().filter(fk -> "dbo".equalsIgnoreCase(fk[0])
                && childTable.equalsIgnoreCase(fk[1])
                && childColumn.equalsIgnoreCase(fk[2])
                && "dbo".equalsIgnoreCase(fk[3])
                && parentTable.equalsIgnoreCase(fk[4])
                && parentColumn.equalsIgnoreCase(fk[5])).count();
        assertEquals(1L, fallbackMatches,
                "HARD STOP: missing, hidden or ambiguous FK dbo." + childTable
                        + "." + childColumn + " -> dbo." + parentTable + "." + parentColumn);
    }

    private static final class FixtureIds {
        final Map<String, List<Long>> ids = new LinkedHashMap<>();

        void add(String table, long id) {
            ids.computeIfAbsent(table, k -> new ArrayList<>()).add(id);
        }

        void verifyAbsent(JdbcTemplate db) {
            for (var entry : ids.entrySet()) {
                String pk = switch (entry.getKey()) {
                    case "employees" -> "employee_id";
                    case "accounts" -> "account_id";
                    case "employee_assignments" -> "assignment_id";
                    case "hr_department_scopes" -> "scope_id";
                    case "work_schedules" -> "schedule_id";
                    case "attendance_records" -> "attendance_id";
                    case "audit_logs" -> "audit_id";
                    default -> throw new IllegalStateException("Unexpected fixture table: " + entry.getKey());
                };
                for (Long id : entry.getValue()) {
                    assertEquals(0L, count(db, "SELECT COUNT_BIG(*) FROM dbo." + entry.getKey()
                            + " WHERE " + pk + "=?", id),
                            "CRITICAL: fixture persisted in dbo." + entry.getKey() + " ID=" + id);
                }
            }
        }
    }

    private static long id(JdbcTemplate db, String table, String idCol, String roleCode) {
        Long result = db.queryForObject(
                "SELECT " + idCol + " FROM dbo." + table + " WHERE role_code=?",
                Long.class, roleCode);
        assertNotNull(result);
        return result;
    }

    private static long employee(JdbcTemplate db, String code, String label, LocalDate hireDate) {
        Long n = db.queryForObject("""
                INSERT INTO dbo.employees(employee_code, full_name, hire_date, employment_status, created_at)
                OUTPUT INSERTED.employee_id
                VALUES (?, ?, ?, 'DANG_LAM', SYSUTCDATETIME())
                """, Long.class, code, label, hireDate);
        assertNotNull(n);
        return n;
    }

    private static long account(JdbcTemplate db, long employeeId, long roleId, String username) {
        // An unusable test-only placeholder; not intended for actual authentication.
        String unusableHash = "!".repeat(70);
        Long n = db.queryForObject(
                """
                        INSERT INTO dbo.accounts(employee_id, role_id, username, password_hash, is_active, token_version, created_at)
                        OUTPUT INSERTED.account_id
                        VALUES (?, ?, ?, ?, 1, 0, SYSUTCDATETIME())
                        """,
                Long.class, employeeId, roleId, username, unusableHash);
        assertNotNull(n);
        return n;
    }

    private static long schedule(JdbcTemplate db, long employeeId, LocalDate day) {
        LocalDateTime start = day.atTime(8, 0);
        Long n = db.queryForObject("""
                INSERT INTO dbo.work_schedules
                (employee_id, shift_id, work_date, planned_start, planned_end, planned_break_minutes, note, created_at)
                OUTPUT INSERTED.schedule_id
                VALUES (?, NULL, ?, ?, ?, 60, ?, SYSUTCDATETIME())
                """, Long.class, employeeId, day, start, day.atTime(17, 0), PREFIX + "fixture");
        assertNotNull(n);
        return n;
    }

    private static long attendance(JdbcTemplate db, long employeeId, long scheduleId,
            LocalDate day, long confirmer, String marker) {
        Long n = db.queryForObject("""
                INSERT INTO dbo.attendance_records
                (employee_id,schedule_id,work_date,actual_start,actual_end,actual_break_minutes,
                 attendance_result,verification_status,confirmed_by,confirmed_at,correction_reason,
                 created_by,created_at)
                OUTPUT INSERTED.attendance_id
                VALUES (?,?,?,?,?,60,'DI_LAM','DA_XAC_NHAN',?,SYSUTCDATETIME(),?,?,SYSUTCDATETIME())
                """, Long.class, employeeId, scheduleId, day, day.atTime(8, 0),
                day.atTime(17, 0), confirmer, marker, confirmer);
        assertNotNull(n);
        return n;
    }

    private static long auditCount(JdbcTemplate db, long attendanceId) {
        return count(db, """
                SELECT COUNT_BIG(*) FROM dbo.audit_logs
                WHERE target_table='attendance_records' AND target_record_key=?
                  AND action='ATTENDANCE_UNCONFIRM'
                """, Long.toString(attendanceId));
    }

    private static void verifyPreflight(JdbcTemplate db) {
        assertEquals("hrm_test", db.queryForObject("SELECT DB_NAME()", String.class),
                "HARD STOP: wrong SQL Server database");
        requireColumns(db, "employees", "employee_id", "employee_code", "full_name",
                "hire_date", "employment_status", "created_at");
        requireColumns(db, "accounts", "account_id", "employee_id", "role_id", "username",
                "password_hash", "is_active", "token_version", "created_at");
        requireColumns(db, "employee_assignments", "employee_id", "department_id", "position_id",
                "effective_from", "effective_to");
        requireColumns(db, "hr_department_scopes", "hr_account_id", "department_id",
                "effective_from", "effective_to", "revoked_at", "created_at", "created_by");
        requireColumns(db, "work_schedules", "employee_id", "shift_id", "work_date",
                "planned_start", "planned_end", "planned_break_minutes", "note", "created_at");
        requireColumns(db, "attendance_records", "employee_id", "schedule_id", "work_date",
                "actual_start", "actual_end", "actual_break_minutes", "attendance_result",
                "verification_status", "confirmed_by", "confirmed_at", "correction_reason", "created_by");
        requireColumns(db, "audit_logs", "actor_account_id", "action", "target_table",
                "target_record_key", "reason", "before_data_json", "after_data_json");
        requireColumns(db, "employee_assignments", "assignment_id");
        requireColumns(db, "hr_department_scopes", "scope_id");
        requireColumns(db, "work_schedules", "schedule_id");
        requireColumns(db, "attendance_records", "attendance_id");
        requireColumns(db, "audit_logs", "audit_id");
        List<String> fixtureColumnProblems = new ArrayList<>();
        // Do not silently depend on nullable fields/defaults from an outdated SQL
        // script.
        requireInsertShape(db, "employees",
                new String[] { "employee_code", "full_name", "hire_date", "employment_status", "created_at" },
                new String[] {}, fixtureColumnProblems);
        requireInsertShape(db, "accounts",
                new String[] { "employee_id", "role_id", "username", "password_hash", "is_active", "token_version",
                        "created_at" },
                new String[] {}, fixtureColumnProblems);
        requireInsertShape(db, "employee_assignments",
                new String[] { "employee_id", "department_id", "position_id", "effective_from", "created_at" },
                new String[] { "effective_to" }, fixtureColumnProblems);
        requireInsertShape(db, "hr_department_scopes",
                new String[] { "hr_account_id", "department_id", "effective_from", "effective_to", "revoked_at",
                        "created_at", "created_by" },
                new String[] {}, fixtureColumnProblems);
        requireInsertShape(db, "work_schedules",
                new String[] { "employee_id", "shift_id", "work_date", "planned_start", "planned_end",
                        "planned_break_minutes", "note", "created_at" },
                new String[] { "shift_id" }, fixtureColumnProblems);
        requireInsertShape(db, "attendance_records",
                new String[] { "employee_id", "schedule_id", "work_date", "actual_start", "actual_end",
                        "actual_break_minutes",
                        "attendance_result", "verification_status", "confirmed_by", "confirmed_at", "correction_reason",
                        "created_by", "created_at" },
                new String[] {}, fixtureColumnProblems);
        // Shared AttendanceService.writeAudit() supplies occurred_at using
        // SYSUTCDATETIME().
        requireInsertShape(db, "audit_logs",
                new String[] { "actor_account_id", "occurred_at", "action", "target_table", "target_record_key",
                        "reason", "before_data_json", "after_data_json" },
                new String[] {}, fixtureColumnProblems);
        assertTrue(fixtureColumnProblems.isEmpty(),
                "HARD STOP: fixture INSERT/schema mismatches (all tables):\n - "
                        + String.join("\n - ", fixtureColumnProblems));
        requireForeignKey(db, "accounts", "employee_id", "employees", "employee_id");
        requireForeignKey(db, "accounts", "role_id", "roles", "role_id");
        requireForeignKey(db, "employee_assignments", "employee_id", "employees", "employee_id");
        requireForeignKey(db, "employee_assignments", "department_id", "departments", "department_id");
        requireForeignKey(db, "employee_assignments", "position_id", "positions", "position_id");
        requireForeignKey(db, "work_schedules", "employee_id", "employees", "employee_id");
        requireForeignKey(db, "attendance_records", "employee_id", "employees", "employee_id");
        requireForeignKey(db, "attendance_records", "confirmed_by", "accounts", "account_id");
        requireForeignKey(db, "hr_department_scopes", "hr_account_id", "accounts", "account_id");
        requireForeignKey(db, "hr_department_scopes", "department_id", "departments", "department_id");
        for (String role : new String[] { "ADMIN", "HR", "EMPLOYEE" }) {
            assertEquals(1L, count(db, "SELECT COUNT_BIG(*) FROM dbo.roles WHERE role_code=?", role));
        }
        for (String permission : new String[] { "ATTENDANCE_MANAGE", "ATTENDANCE_CONFIRM", "ATTENDANCE_UNCONFIRM" }) {
            for (String role : new String[] { "ADMIN", "HR" }) {
                assertEquals(1L, count(db, """
                        SELECT COUNT_BIG(*) FROM dbo.role_permissions rp
                        JOIN dbo.roles r ON r.role_id=rp.role_id
                        JOIN dbo.permissions p ON p.permission_id=rp.permission_id
                        WHERE r.role_code=? AND p.permission_code=?
                        """, role, permission), "Required role permission missing");
            }
        }
        assertTrue(count(db, """
                SELECT COUNT_BIG(*) FROM dbo.departments WHERE is_active=1
                """) >= 2, "HARD STOP: IT-04 requires two existing active departments");
        assertTrue(count(db, """
                SELECT COUNT_BIG(*) FROM dbo.positions WHERE is_active=1
                """) > 0, "No active position; stop");
    }

    @FunctionalInterface
    private interface CheckedAction {
        void run();
    }

    private static void expectForbidden(CheckedAction action) {
        ResponseStatusException error = assertThrows(ResponseStatusException.class, action::run);
        assertEquals(HttpStatus.FORBIDDEN, error.getStatusCode());
    }

    private static long trackedEmployee(JdbcTemplate db, FixtureIds ids, String code, LocalDate hire) {
        long n = employee(db, code, "IT04 isolated fixture", hire);
        ids.add("employees", n);
        return n;
    }

    private static long trackedAccount(JdbcTemplate db, FixtureIds ids, long emp, String role, String code) {
        long n = account(db, emp, id(db, "roles", "role_id", role), code.toLowerCase());
        ids.add("accounts", n);
        return n;
    }

    private static void trackedAssignment(JdbcTemplate db, FixtureIds ids, long emp, long dept, long position,
            LocalDate from) {
        Long n = db.queryForObject("""
                INSERT dbo.employee_assignments(employee_id,department_id,position_id,effective_from,created_at)
                OUTPUT INSERTED.assignment_id VALUES (?,?,?,?,SYSUTCDATETIME())
                """, Long.class, emp, dept, position, from);
        assertNotNull(n);
        ids.add("employee_assignments", n);
    }

    private static long trackedScope(JdbcTemplate db, FixtureIds ids, long hr, long dept, long admin) {
        Long n = db.queryForObject(
                """
                        INSERT dbo.hr_department_scopes(hr_account_id,department_id,effective_from,effective_to,revoked_at,created_at,created_by)
                        OUTPUT INSERTED.scope_id VALUES (?,?,DATEADD(DAY,-1,SYSUTCDATETIME()),NULL,NULL,SYSUTCDATETIME(),?)
                        """,
                Long.class, hr, dept, admin);
        assertNotNull(n);
        ids.add("hr_department_scopes", n);
        return n;
    }

    private static long trackedSchedule(JdbcTemplate db, FixtureIds ids, long emp, LocalDate date) {
        long n = schedule(db, emp, date);
        ids.add("work_schedules", n);
        return n;
    }

    private static long trackedAttendance(JdbcTemplate db, FixtureIds ids, long emp, long sched, LocalDate date,
            long confirm, String marker) {
        long n = attendance(db, emp, sched, date, confirm, marker);
        ids.add("attendance_records", n);
        return n;
    }

    private static long trackedUnconfirmed(JdbcTemplate db, FixtureIds ids, long emp, LocalDate date, long creator,
            String marker) {
        long sched = trackedSchedule(db, ids, emp, date);
        long att = trackedAttendance(db, ids, emp, sched, date, creator, marker);
        db.update(
                "UPDATE dbo.attendance_records SET verification_status='CHUA_XAC_NHAN',confirmed_by=NULL,confirmed_at=NULL WHERE attendance_id=?",
                att);
        return att;
    }

    private static long trackedCorrectionAudit(JdbcTemplate db, long att, long actor, String marker) {
        Long n = db.queryForObject(
                """
                        INSERT INTO dbo.audit_logs(actor_account_id,occurred_at,action,target_table,target_record_key,reason,before_data_json,after_data_json)
                        OUTPUT INSERTED.audit_id
                        VALUES (?,SYSUTCDATETIME(),'ATTENDANCE_CORRECT','attendance_records',?,?,NULL,NULL)
                        """,
                Long.class, actor, Long.toString(att), marker);
        assertNotNull(n);
        return n;
    }

    @Test
    void rbacSqlServerMatrixRollbackOnly() throws Exception {
        Assumptions.assumeTrue(enabled(), "SQL write test has NOT been approved; skip");
        String url = env("HRM_T8_IT_URL");
        Assumptions.assumeTrue(TEST_URL.matcher(url).find(), "Explicit databaseName=hrm_test required");
        String user = env("HRM_T8_IT_USER");
        String password = env("HRM_T8_IT_PASSWORD");
        try (var probe = DriverManager.getConnection(url, user, password)) {
            assertEquals("hrm_test", probe.getCatalog(), "HARD STOP: incorrect catalog");
        }

        DriverManagerDataSource ds = new DriverManagerDataSource(url, user, password);
        JdbcTemplate db = new JdbcTemplate(ds);
        JdbcTransactionManager transactionManager = new JdbcTransactionManager(ds);
        TransactionTemplate template = new TransactionTemplate(transactionManager);
        verifyPreflight(db);

        String run = PREFIX + UUID.randomUUID().toString().replace("-", "").substring(0, 14);
        long baselineEmployees = count(db,
                "SELECT COUNT_BIG(*) FROM dbo.employees WHERE employee_code LIKE 'T8[_]IT04[_]%'");
        long baselineAccounts = count(db, "SELECT COUNT_BIG(*) FROM dbo.accounts WHERE username LIKE 't8[_]it[_]%'");
        long baselineAudit = count(db, """
                SELECT COUNT_BIG(*) FROM dbo.audit_logs WHERE reason LIKE 'T8[_]IT04[_]%'
                """);

        FixtureIds fixtureIds = new FixtureIds();
        try {
            template.executeWithoutResult(status -> {
                // Set first; even a failed assertion must not commit fixture DML.
                status.setRollbackOnly();
                assertEquals("hrm_test", db.queryForObject("SELECT DB_NAME()", String.class));
                assertEquals(0L, count(db,
                        "SELECT COUNT_BIG(*) FROM dbo.employees WHERE employee_code LIKE ?", run + "%"));

                // Fail closed before any write when two independent active departments are
                // unavailable.
                List<Long> departments = db.queryForList("""
                        SELECT department_id FROM dbo.departments
                        WHERE is_active=1 ORDER BY department_id
                        """, Long.class);
                assertTrue(departments.size() >= 2,
                        "HARD STOP: IT-04 needs two existing active departments; do not create one");
                long deptA = departments.get(0), deptB = departments.get(1);
                Long position = db.queryForObject("""
                        SELECT TOP(1) position_id FROM dbo.positions WHERE is_active=1
                        ORDER BY position_id
                        """, Long.class);
                assertNotNull(position);
                LocalDate date = LocalDate.now().minusDays(1);
                LocalDate hireDate = date.minusMonths(2);
                long adminEmp = trackedEmployee(db, fixtureIds, run + "ADM", hireDate);
                long hrAEmp = trackedEmployee(db, fixtureIds, run + "HRA", hireDate);
                long hrBEmp = trackedEmployee(db, fixtureIds, run + "HRB", hireDate);
                long empA = trackedEmployee(db, fixtureIds, run + "EA", hireDate);
                long empB = trackedEmployee(db, fixtureIds, run + "EB", hireDate);
                long inactiveEmp = trackedEmployee(db, fixtureIds, run + "EI", hireDate);
                long noAccountEmp = trackedEmployee(db, fixtureIds, run + "EN", hireDate);
                long admin = trackedAccount(db, fixtureIds, adminEmp, "ADMIN", run + "ADM");
                long hrA = trackedAccount(db, fixtureIds, hrAEmp, "HR", run + "HRA");
                long hrB = trackedAccount(db, fixtureIds, hrBEmp, "HR", run + "HRB");
                long employeeA = trackedAccount(db, fixtureIds, empA, "EMPLOYEE", run + "EA");
                trackedAccount(db, fixtureIds, empB, "EMPLOYEE", run + "EB");
                long inactiveAccount = trackedAccount(db, fixtureIds, inactiveEmp, "EMPLOYEE", run + "EI");
                db.update("UPDATE dbo.accounts SET is_active=0 WHERE account_id=?", inactiveAccount);
                for (long emp : new long[] { empA, inactiveEmp, noAccountEmp })
                    trackedAssignment(db, fixtureIds, emp, deptA, position, hireDate);
                trackedAssignment(db, fixtureIds, empB, deptB, position, hireDate);
                long scopeA = trackedScope(db, fixtureIds, hrA, deptA, admin);
                trackedScope(db, fixtureIds, hrB, deptB, admin);
                long schA = trackedSchedule(db, fixtureIds, empA, date);
                long confirmedByAdmin = trackedAttendance(db, fixtureIds, empA, schA, date, admin, run + "_1");
                long schA2 = trackedSchedule(db, fixtureIds, empA, date.minusDays(1));
                long confirmedByHrA = trackedAttendance(db, fixtureIds, empA, schA2, date.minusDays(1), hrA,
                        run + "_2");
                var permissions = new AttendancePermissionService(db);
                assertEquals("ADMIN", permissions.requireAttendanceAccess(admin, empB, date,
                        AttendancePermissionService.MANAGE).roleCode()); // IT-04.01
                assertEquals("HR", permissions.requireAttendanceAccess(hrA, empA, date,
                        AttendancePermissionService.MANAGE).roleCode()); // IT-04.02
                expectForbidden(() -> permissions.requireAttendanceAccess(hrA, empB, date,
                        AttendancePermissionService.MANAGE)); // IT-04.03
                db.update(
                        "UPDATE dbo.hr_department_scopes SET revoked_at=SYSUTCDATETIME(), revoked_by=? WHERE scope_id=?",
                        admin, scopeA);
                expectForbidden(() -> permissions.requireAttendanceAccess(hrA, empA, date,
                        AttendancePermissionService.MANAGE)); // IT-04.04
                db.update("UPDATE dbo.hr_department_scopes SET revoked_at=NULL, revoked_by=NULL WHERE scope_id=?",
                        scopeA);
                expectForbidden(() -> permissions.requireAttendanceAccess(hrA, inactiveEmp, date,
                        AttendancePermissionService.MANAGE)); // IT-04.05 inactive
                expectForbidden(() -> permissions.requireAttendanceAccess(hrA, noAccountEmp, date,
                        AttendancePermissionService.MANAGE)); // IT-04.05 no account
                expectForbidden(() -> permissions.requireAttendanceAccess(employeeA, empA, date,
                        AttendancePermissionService.CONFIRM)); // IT-04.06
                var service = new AttendanceService(db, permissions);
                long auditedBefore = count(db,
                        "SELECT COUNT_BIG(*) FROM dbo.audit_logs WHERE target_table='attendance_records' AND target_record_key=?",
                        Long.toString(confirmedByAdmin));
                // IT-04.10: HR-A must not unconfirm own approval.
                expectForbidden(() -> service.unconfirm(confirmedByHrA, run + "_self", hrA));
                assertEquals("DA_XAC_NHAN", service.get(confirmedByHrA).verificationStatus());
                // IT-04.11: HR-A can unconfirm ADMIN's approval for its in-scope EMPLOYEE.
                var successful = service.unconfirm(confirmedByAdmin, run + "_other", hrA);
                assertEquals("CHUA_XAC_NHAN", successful.verificationStatus());
                assertEquals(auditedBefore + 1, count(db,
                        "SELECT COUNT_BIG(*) FROM dbo.audit_logs WHERE target_table='attendance_records' AND target_record_key=?",
                        Long.toString(confirmedByAdmin)));
                // IT-04.07: HR-A cannot confirm attendance originally created by HR-A.
                long selfCreated = trackedUnconfirmed(db, fixtureIds, empA, date.plusDays(1), hrA, run + "_creator");
                expectForbidden(() -> service.confirm(selfCreated, hrA));
                // IT-04.08: HR-A cannot confirm attendance last corrected by HR-A.
                long selfCorrected = trackedUnconfirmed(db, fixtureIds, empA, date.plusDays(2), admin,
                        run + "_corrector");
                long correctionAudit = trackedCorrectionAudit(db, selfCorrected, hrA, run + "_corrector");
                fixtureIds.add("audit_logs", correctionAudit);
                expectForbidden(() -> service.confirm(selfCorrected, hrA));
                // IT-04.09: Legacy record with NULL created_by is not HR-confirmable.
                long noCreator = trackedUnconfirmed(db, fixtureIds, empA, date.plusDays(3), admin, run + "_legacy");
                db.update("UPDATE dbo.attendance_records SET created_by=NULL WHERE attendance_id=?", noCreator);
                expectForbidden(() -> service.confirm(noCreator, hrA));
                // IT-04.12: eligibility is assessed at work_date; use fixture-only update.
                db.update("UPDATE dbo.employees SET employment_status='DA_NGHI',termination_date=? WHERE employee_id=?",
                        date, empA);
                expectForbidden(() -> permissions.requireAttendanceAccess(hrA, empA, date,
                        AttendancePermissionService.MANAGE));
                db.update(
                        "UPDATE dbo.employees SET employment_status='DANG_LAM',termination_date=NULL WHERE employee_id=?",
                        empA);
                // Explicit postconditions for denied operations.
                for (long att : new long[] { confirmedByHrA })
                    assertEquals("DA_XAC_NHAN", service.get(att).verificationStatus());
                for (long att : new long[] { selfCreated, selfCorrected, noCreator })
                    assertEquals("CHUA_XAC_NHAN", service.get(att).verificationStatus());
                for (long att : new long[] { confirmedByAdmin, selfCreated, selfCorrected, noCreator, confirmedByHrA })
                    for (Long auditId : db.queryForList(
                            "SELECT audit_id FROM dbo.audit_logs WHERE target_table='attendance_records' AND target_record_key=?",
                            Long.class, Long.toString(att)))
                        fixtureIds.add("audit_logs", auditId);

            });
        } finally {
            fixtureIds.verifyAbsent(db);
            assertEquals(baselineEmployees, count(db,
                    "SELECT COUNT_BIG(*) FROM dbo.employees WHERE employee_code LIKE 'T8[_]IT04[_]%'"),
                    "CRITICAL: fixture employees persisted after rollback");
            assertEquals(baselineAccounts, count(db,
                    "SELECT COUNT_BIG(*) FROM dbo.accounts WHERE username LIKE 't8[_]it[_]%'"),
                    "CRITICAL: fixture accounts persisted after rollback");
            assertEquals(baselineAudit, count(db,
                    "SELECT COUNT_BIG(*) FROM dbo.audit_logs WHERE reason LIKE 'T8[_]IT04[_]%'"),
                    "CRITICAL: fixture audit persisted after rollback");
        }
    }
}
