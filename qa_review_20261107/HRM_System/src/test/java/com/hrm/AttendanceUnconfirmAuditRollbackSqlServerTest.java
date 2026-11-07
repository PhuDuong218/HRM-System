package com.hrm;

import static org.junit.jupiter.api.Assertions.*;

import java.sql.DriverManager;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.support.JdbcTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.hrm.service.AttendancePermissionService;
import com.hrm.service.AttendanceService;

/**
 * IT-02: Real SQL Server UPDATE, injected failure at the audit INSERT boundary,
 * explicit savepoint rollback, and an outer rollback-only fixture transaction.
 *
 * This is NOT a proof of @Transactional proxy rollback or of a database-generated
 * audit constraint error. Run ONLY after separate authorization to write hrm_test.
 * Requires the current Task 8 service including audit_logs.occurred_at fix.
 */
class AttendanceUnconfirmAuditRollbackSqlServerTest {
    private static final Pattern TEST_URL = Pattern.compile(
            "(?is)(?:^|;)\\s*databaseName\\s*=\\s*hrm_test\\s*(?:;|$)");
    private static final String PREFIX = "T8_IT_";

    private static boolean enabled() {
        return "true".equalsIgnoreCase(System.getenv("HRM_T8_IT_ENABLE"))
                && "true".equalsIgnoreCase(System.getenv("HRM_T8_IT_WRITE_APPROVED"))
                && "true".equalsIgnoreCase(System.getenv("HRM_T8_IT_ROLLBACK_FIXTURE_APPROVED"))
                && "true".equalsIgnoreCase(System.getenv("HRM_T8_IT02_AUDIT_FAIL_APPROVED"));
    }

    private static String requiredEnv(String key) {
        String value = System.getenv(key);
        Assumptions.assumeTrue(value != null && !value.isBlank(), key + " is required");
        return value;
    }

    private static long count(JdbcTemplate db, String sql, Object... args) {
        Long result = db.queryForObject(sql, Long.class, args);
        assertNotNull(result);
        return result;
    }

    private static long insertId(JdbcTemplate db, String sql, Object... params) {
        Long id = db.queryForObject(sql, Long.class, params);
        assertNotNull(id);
        return id;
    }

    private static void verifyPreflight(JdbcTemplate db) {
        assertEquals("hrm_test", db.queryForObject("SELECT DB_NAME()", String.class),
                "HARD STOP: wrong database");
        for (String table : List.of("employees", "accounts", "roles", "permissions",
                "role_permissions", "departments", "positions", "employee_assignments",
                "work_schedules", "attendance_records", "audit_logs")) {
            assertNotNull(db.queryForObject("SELECT OBJECT_ID(?)", Integer.class, "dbo." + table),
                    "HARD STOP: missing table dbo." + table);
        }
        for (String spec : List.of("employees:employee_id,employee_code,full_name,hire_date,employment_status,created_at",
                "accounts:account_id,employee_id,role_id,username,password_hash,is_active,token_version,created_at",
                "employee_assignments:assignment_id,employee_id,department_id,position_id,effective_from,created_at",
                "work_schedules:schedule_id,employee_id,shift_id,work_date,planned_start,planned_end,planned_break_minutes,note,created_at",
                "attendance_records:attendance_id,employee_id,schedule_id,work_date,actual_start,actual_end,actual_break_minutes,attendance_result,verification_status,confirmed_by,confirmed_at,correction_reason,created_by,created_at",
                "audit_logs:audit_id,actor_account_id,occurred_at,action,target_table,target_record_key,reason,before_data_json,after_data_json")) {
            String[] parts = spec.split(":", 2);
            var cols = db.queryForList("""
                    SELECT COLUMN_NAME FROM INFORMATION_SCHEMA.COLUMNS
                    WHERE TABLE_SCHEMA='dbo' AND TABLE_NAME=?
                    """, String.class, parts[0]);
            for (String col : parts[1].split(","))
                assertTrue(cols.contains(col), "HARD STOP: missing dbo." + parts[0] + "." + col);
        }
        // Validate that all columns omitted by fixture INSERT can legally be omitted.
        validateInsertShape(db, "employees", "employee_code,full_name,hire_date,employment_status,created_at");
        validateInsertShape(db, "accounts", "employee_id,role_id,username,password_hash,is_active,token_version,created_at");
        validateInsertShape(db, "employee_assignments", "employee_id,department_id,position_id,effective_from,created_at");
        validateInsertShape(db, "work_schedules", "employee_id,shift_id,work_date,planned_start,planned_end,planned_break_minutes,note,created_at");
        validateInsertShape(db, "attendance_records", "employee_id,schedule_id,work_date,actual_start,actual_end,actual_break_minutes,attendance_result,verification_status,confirmed_by,confirmed_at,correction_reason,created_by,created_at");
        validateInsertShape(db, "audit_logs", "actor_account_id,occurred_at,action,target_table,target_record_key,reason,before_data_json,after_data_json");
        assertEquals(1L, count(db, """
                SELECT COUNT_BIG(*) FROM dbo.role_permissions rp
                JOIN dbo.roles r ON r.role_id=rp.role_id
                JOIN dbo.permissions p ON p.permission_id=rp.permission_id
                WHERE r.role_code='ADMIN' AND p.permission_code='ATTENDANCE_UNCONFIRM'
                """), "HARD STOP: ADMIN permission missing");
        assertTrue(count(db, "SELECT COUNT_BIG(*) FROM dbo.departments WHERE is_active=1") > 0);
        assertTrue(count(db, "SELECT COUNT_BIG(*) FROM dbo.positions WHERE is_active=1") > 0);
    }

    private static void validateInsertShape(JdbcTemplate db, String table, String csv) {
        var supplied = java.util.Set.of(csv.split(","));
        var columns = db.queryForList("""
                SELECT COLUMN_NAME, DATA_TYPE, IS_NULLABLE, COLUMN_DEFAULT,
                  COLUMNPROPERTY(OBJECT_ID('dbo.' + TABLE_NAME), COLUMN_NAME, 'IsIdentity') AS is_identity,
                  COLUMNPROPERTY(OBJECT_ID('dbo.' + TABLE_NAME), COLUMN_NAME, 'IsComputed') AS is_computed
                FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA='dbo' AND TABLE_NAME=?
                """, table);
        List<String> faults = new ArrayList<>();
        for (var col : columns) {
            String name = (String) col.get("COLUMN_NAME");
            String type = String.valueOf(col.get("DATA_TYPE"));
            boolean generated = "timestamp".equalsIgnoreCase(type) || "rowversion".equalsIgnoreCase(type)
                    || Integer.valueOf(1).equals(col.get("is_identity"))
                    || Integer.valueOf(1).equals(col.get("is_computed"));
            boolean optional = generated || "YES".equals(col.get("IS_NULLABLE"))
                    || col.get("COLUMN_DEFAULT") != null;
            if (!supplied.contains(name) && !optional) faults.add(name + " is required");
            if (supplied.contains(name) && generated) faults.add(name + " is server-generated");
        }
        for (String name : supplied) {
            boolean found = columns.stream().anyMatch(c -> name.equals(c.get("COLUMN_NAME")));
            if (!found) faults.add(name + " does not exist");
        }
        assertTrue(faults.isEmpty(), "HARD STOP: dbo." + table + " insert mismatch: " + faults);
    }

    private static final class AuditFaultJdbcTemplate extends JdbcTemplate {
        private final JdbcTemplate observer;
        private final long targetAttendanceId;
        private final AtomicBoolean sawRealUpdate;

        AuditFaultJdbcTemplate(DriverManagerDataSource ds, JdbcTemplate observer,
                               long targetAttendanceId, AtomicBoolean sawRealUpdate) {
            super(ds);
            this.observer = observer;
            this.targetAttendanceId = targetAttendanceId;
            this.sawRealUpdate = sawRealUpdate;
        }

        @Override
        public int update(String sql, Object... args) {
            if (sql.contains("INSERT INTO dbo.audit_logs") && args.length >= 2
                    && "ATTENDANCE_UNCONFIRM".equals(args[1])) {
                String status = observer.queryForObject("""
                        SELECT verification_status FROM dbo.attendance_records
                        WHERE attendance_id=?
                        """, String.class, targetAttendanceId);
                Long confirmer = observer.queryForObject("""
                        SELECT confirmed_by FROM dbo.attendance_records WHERE attendance_id=?
                        """, Long.class, targetAttendanceId);
                sawRealUpdate.set("CHUA_XAC_NHAN".equals(status) && confirmer == null);
                throw new DataIntegrityViolationException("IT-02 injected audit INSERT failure; no SQL audit insert executed");
            }
            return super.update(sql, args);
        }
    }

    @Test
    void auditFailureAndExplicitSavepointRollback() throws Exception {
        Assumptions.assumeTrue(enabled(), "IT-02 DB writes not separately approved; skipped");
        String url = requiredEnv("HRM_T8_IT_URL");
        Assumptions.assumeTrue(TEST_URL.matcher(url).find(), "HARD STOP: exact hrm_test URL required");
        String user = requiredEnv("HRM_T8_IT_USER");
        String password = requiredEnv("HRM_T8_IT_PASSWORD");
        try (var connection = DriverManager.getConnection(url, user, password)) {
            assertEquals("hrm_test", connection.getCatalog());
            try (var statement = connection.createStatement(); var rs = statement.executeQuery("SELECT DB_NAME()")) {
                assertTrue(rs.next()); assertEquals("hrm_test", rs.getString(1));
            }
        }
        DriverManagerDataSource ds = new DriverManagerDataSource(url, user, password);
        JdbcTemplate db = new JdbcTemplate(ds);
        verifyPreflight(db); // all read-only, before any fixture INSERT
        JdbcTransactionManager manager = new JdbcTransactionManager(ds);
        TransactionTemplate tx = new TransactionTemplate(manager);
        String run = PREFIX + UUID.randomUUID().toString().replace("-", "").substring(0, 14);
        List<Long> employeeIds = new ArrayList<>(), accountIds = new ArrayList<>(), assignmentIds = new ArrayList<>(),
                scheduleIds = new ArrayList<>(), attendanceIds = new ArrayList<>(), auditIds = new ArrayList<>();
        long baselineEmployee = count(db, "SELECT COUNT_BIG(*) FROM dbo.employees WHERE employee_code LIKE 'T8[_]IT[_]%'");
        long baselineAccount = count(db, "SELECT COUNT_BIG(*) FROM dbo.accounts WHERE username LIKE 't8[_]it[_]%'");
        long baselineAudit = count(db, "SELECT COUNT_BIG(*) FROM dbo.audit_logs WHERE reason LIKE 'T8[_]IT[_]%'");
        try {
            tx.executeWithoutResult(status -> {
                status.setRollbackOnly(); // BEFORE ANY DML
                assertEquals("hrm_test", db.queryForObject("SELECT DB_NAME()", String.class));
                assertEquals(0L, count(db, "SELECT COUNT_BIG(*) FROM dbo.employees WHERE employee_code LIKE ?", run + "%"));
                LocalDate day = LocalDate.now().minusDays(1);
                LocalDate hire = day.minusMonths(2);
                long adminEmployee = insertId(db, """
                        INSERT INTO dbo.employees(employee_code,full_name,hire_date,employment_status,created_at)
                        OUTPUT INSERTED.employee_id VALUES (?, 'IT02 admin', ?, 'DANG_LAM', SYSUTCDATETIME())
                        """, run + "A", hire);
                employeeIds.add(adminEmployee);
                long targetEmployee = insertId(db, """
                        INSERT INTO dbo.employees(employee_code,full_name,hire_date,employment_status,created_at)
                        OUTPUT INSERTED.employee_id VALUES (?, 'IT02 employee', ?, 'DANG_LAM', SYSUTCDATETIME())
                        """, run + "E", hire);
                employeeIds.add(targetEmployee);
                Long adminRole = db.queryForObject("SELECT role_id FROM dbo.roles WHERE role_code='ADMIN'", Long.class);
                Long employeeRole = db.queryForObject("SELECT role_id FROM dbo.roles WHERE role_code='EMPLOYEE'", Long.class);
                assertNotNull(adminRole); assertNotNull(employeeRole);
                String unusableHash = "!".repeat(70);
                long admin = insertId(db, """
                        INSERT INTO dbo.accounts(employee_id,role_id,username,password_hash,is_active,token_version,created_at)
                        OUTPUT INSERTED.account_id VALUES (?,?,?,?,1,0,SYSUTCDATETIME())
                        """, adminEmployee, adminRole, (run + "A").toLowerCase(), unusableHash);
                accountIds.add(admin);
                long employeeAccount = insertId(db, """
                        INSERT INTO dbo.accounts(employee_id,role_id,username,password_hash,is_active,token_version,created_at)
                        OUTPUT INSERTED.account_id VALUES (?,?,?,?,1,0,SYSUTCDATETIME())
                        """, targetEmployee, employeeRole, (run + "E").toLowerCase(), unusableHash);
                accountIds.add(employeeAccount);
                Long dept = db.queryForObject("SELECT TOP(1) department_id FROM dbo.departments WHERE is_active=1 ORDER BY department_id", Long.class);
                Long position = db.queryForObject("SELECT TOP(1) position_id FROM dbo.positions WHERE is_active=1 ORDER BY position_id", Long.class);
                assertNotNull(dept); assertNotNull(position);
                long assignment = insertId(db, """
                        INSERT INTO dbo.employee_assignments(employee_id,department_id,position_id,effective_from,created_at)
                        OUTPUT INSERTED.assignment_id VALUES (?,?,?,?,SYSUTCDATETIME())
                        """, targetEmployee, dept, position, hire);
                assignmentIds.add(assignment);
                LocalDateTime begin = day.atTime(8, 0);
                long schedule = insertId(db, """
                        INSERT INTO dbo.work_schedules(employee_id,shift_id,work_date,planned_start,planned_end,planned_break_minutes,note,created_at)
                        OUTPUT INSERTED.schedule_id VALUES (?,NULL,?,?,?,60,?,SYSUTCDATETIME())
                        """, targetEmployee, day, begin, day.atTime(17, 0), run + "_schedule");
                scheduleIds.add(schedule);
                long attendance = insertId(db, """
                        INSERT INTO dbo.attendance_records
                          (employee_id,schedule_id,work_date,actual_start,actual_end,actual_break_minutes,
                           attendance_result,verification_status,confirmed_by,confirmed_at,correction_reason,created_by,created_at)
                        OUTPUT INSERTED.attendance_id
                        VALUES (?,?,?,?,?,60,'DI_LAM','DA_XAC_NHAN',?,SYSUTCDATETIME(),?,?,SYSUTCDATETIME())
                        """, targetEmployee, schedule, day, begin, day.atTime(17, 0), admin, run + "_confirmed", admin);
                attendanceIds.add(attendance);
                var before = new AttendanceService(db, new AttendancePermissionService(db)).get(attendance);
                assertEquals("DA_XAC_NHAN", before.verificationStatus());
                assertEquals(admin, before.confirmedBy());
                long auditsBefore = count(db, """
                        SELECT COUNT_BIG(*) FROM dbo.audit_logs WHERE target_table='attendance_records'
                         AND target_record_key=? AND action='ATTENDANCE_UNCONFIRM'
                        """, Long.toString(attendance));
                Object savepoint = status.createSavepoint();
                AtomicBoolean observedUpdate = new AtomicBoolean(false);
                var faultyDb = new AuditFaultJdbcTemplate(ds, db, attendance, observedUpdate);
                var service = new AttendanceService(faultyDb, new AttendancePermissionService(faultyDb));
                assertThrows(DataIntegrityViolationException.class,
                        () -> service.unconfirm(attendance, run + "_forced_failure", admin));
                assertTrue(observedUpdate.get(), "The real SQL UPDATE must occur before injected audit failure");
                var afterFailedCall = new AttendanceService(db, new AttendancePermissionService(db)).get(attendance);
                assertEquals("CHUA_XAC_NHAN", afterFailedCall.verificationStatus());
                assertNull(afterFailedCall.confirmedBy());
                assertNull(afterFailedCall.confirmedAt());
                assertEquals(auditsBefore, count(db, """
                        SELECT COUNT_BIG(*) FROM dbo.audit_logs WHERE target_table='attendance_records'
                         AND target_record_key=? AND action='ATTENDANCE_UNCONFIRM'
                        """, Long.toString(attendance)));
                // Explicit savepoint rollback, not an automatic Spring proxy rollback.
                status.rollbackToSavepoint(savepoint);
                var restored = new AttendanceService(db, new AttendancePermissionService(db)).get(attendance);
                assertEquals("DA_XAC_NHAN", restored.verificationStatus());
                assertEquals(before.confirmedBy(), restored.confirmedBy());
                assertEquals(before.confirmedAt(), restored.confirmedAt());
                assertEquals(before.actualStart(), restored.actualStart());
                assertEquals(before.actualEnd(), restored.actualEnd());
                assertEquals(before.actualBreakMinutes(), restored.actualBreakMinutes());
                assertEquals(before.attendanceResult(), restored.attendanceResult());
                assertEquals(auditsBefore, count(db, """
                        SELECT COUNT_BIG(*) FROM dbo.audit_logs WHERE target_table='attendance_records'
                         AND target_record_key=? AND action='ATTENDANCE_UNCONFIRM'
                        """, Long.toString(attendance)));
                // SQL Server does not support RELEASE SAVEPOINT; outer rollback closes it.
            });
        } finally {
            // Query after outer rollback has completed. Do not issue DELETE.
            for (long n : employeeIds) assertEquals(0L, count(db, "SELECT COUNT_BIG(*) FROM dbo.employees WHERE employee_id=?", n));
            for (long n : accountIds) assertEquals(0L, count(db, "SELECT COUNT_BIG(*) FROM dbo.accounts WHERE account_id=?", n));
            for (long n : assignmentIds) assertEquals(0L, count(db, "SELECT COUNT_BIG(*) FROM dbo.employee_assignments WHERE assignment_id=?", n));
            for (long n : scheduleIds) assertEquals(0L, count(db, "SELECT COUNT_BIG(*) FROM dbo.work_schedules WHERE schedule_id=?", n));
            for (long n : attendanceIds) assertEquals(0L, count(db, "SELECT COUNT_BIG(*) FROM dbo.attendance_records WHERE attendance_id=?", n));
            for (long n : auditIds) assertEquals(0L, count(db, "SELECT COUNT_BIG(*) FROM dbo.audit_logs WHERE audit_id=?", n));
            assertEquals(baselineEmployee, count(db, "SELECT COUNT_BIG(*) FROM dbo.employees WHERE employee_code LIKE 'T8[_]IT[_]%'"));
            assertEquals(baselineAccount, count(db, "SELECT COUNT_BIG(*) FROM dbo.accounts WHERE username LIKE 't8[_]it[_]%'"));
            assertEquals(baselineAudit, count(db, "SELECT COUNT_BIG(*) FROM dbo.audit_logs WHERE reason LIKE 'T8[_]IT[_]%'"));
        }
    }
}
