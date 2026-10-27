package com.hrm.service;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * Employee-owned clock events. Never accepts employeeId or timestamps from
 * clients.
 * Relies on scheduleTransactionManager being bound to the JdbcTemplate
 * datasource.
 */
@Service
public class AttendanceSelfClockService {
    public record ClockEvent(long attendanceId, LocalDateTime recordedAt, boolean alreadyRecorded) {
    }

    private record Schedule(long employeeId, LocalDateTime plannedStart, LocalDateTime plannedEnd) {
    }

    private record Existing(long id, LocalDateTime in, LocalDateTime out) {
    }

    private final JdbcTemplate db;
    private final Clock clock;

    @Autowired
    public AttendanceSelfClockService(JdbcTemplate db) {
        this(db, Clock.systemDefaultZone());
    }

    // Package-visible for deterministic tests.
    AttendanceSelfClockService(JdbcTemplate db, Clock clock) {
        this.db = db;
        this.clock = clock;
    }

    private long requireEmployee(long accountId) {
        if (accountId <= 0)
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid account");
        List<Long> found = db.query("""
                SELECT a.employee_id FROM dbo.accounts a WITH (HOLDLOCK)
                WHERE a.account_id=? AND a.is_active=1 AND a.employee_id IS NOT NULL
                """, (rs, n) -> rs.getLong(1), accountId);
        if (found.size() != 1 || found.get(0) <= 0)
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Account has no active employee identity");
        // Ownership alone is not enough: self-clock is an EMPLOYEE operation.
        // A single person may have several accounts with different roles.
        Long employeeRole = db.queryForObject("""
                SELECT COUNT_BIG(*) FROM dbo.accounts a WITH (HOLDLOCK)
                JOIN dbo.roles r ON r.role_id=a.role_id
                WHERE a.account_id=? AND a.is_active=1 AND r.role_code='EMPLOYEE'
                """, Long.class, accountId);
        if (employeeRole == null || employeeRole != 1L)
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Employee role required for self-clock");
        return found.get(0);
    }

    private Schedule requireSchedule(long scheduleId, long employeeId) {
        var found = db.query("""
                SELECT employee_id, planned_start, planned_end
                FROM dbo.work_schedules WITH (UPDLOCK, HOLDLOCK)
                WHERE schedule_id=? AND employee_id=?
                """,
                (rs, n) -> new Schedule(rs.getLong(1), rs.getTimestamp(2).toLocalDateTime(),
                        rs.getTimestamp(3).toLocalDateTime()),
                scheduleId, employeeId);
        if (found.size() != 1)
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "No owned schedule");
        return found.get(0);
    }

    private Existing existing(long scheduleId) {
        var matches = db.query("""
                SELECT attendance_id,actual_start,actual_end
                FROM dbo.attendance_records WITH (UPDLOCK,HOLDLOCK)
                WHERE schedule_id=? AND invalidated_at IS NULL
                """,
                (rs, n) -> new Existing(rs.getLong(1), timestamp(rs.getTimestamp(2)), timestamp(rs.getTimestamp(3))),
                scheduleId);
        if (matches.size() > 1)
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Duplicate attendance state");
        return matches.isEmpty() ? null : matches.get(0);
    }

    private static LocalDateTime timestamp(Timestamp t) {
        return t == null ? null : t.toLocalDateTime();
    }

    private LocalDateTime now() {
        return LocalDateTime.now(clock);
    }

    private void audit(long actor, long id, String action, String before, String after) {
        db.update(
                """
                        INSERT INTO dbo.audit_logs(actor_account_id,action,target_table,target_record_key,reason,before_data_json,after_data_json)
                        VALUES (?,?,'attendance_records',?,NULL,?,?)
                        """,
                actor, action, Long.toString(id), before, after);
    }

    @Transactional("scheduleTransactionManager")
    public ClockEvent checkIn(long accountId, long scheduleId) {
        long employee = requireEmployee(accountId);
        // Exclusive per employee; protects sibling schedules against concurrent clock
        // operations.
        lockEmployee(employee);
        Schedule s = requireSchedule(scheduleId, employee);
        Existing e = existing(scheduleId);
        if (e != null) {
            if (e.in() != null)
                return new ClockEvent(e.id(), e.in(), true);
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Attendance exists without check-in; HR review required");
        }
        LocalDateTime at = now();
        if (!AttendanceClockPolicy.checkIn(s.plannedStart(), s.plannedEnd(), at).allowed())
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Outside check-in window");
        Long id = db.queryForObject("""
                INSERT INTO dbo.attendance_records(employee_id,schedule_id,work_date,actual_start,actual_end,
                     actual_break_minutes,attendance_result,verification_status,created_by)
                OUTPUT INSERTED.attendance_id
                VALUES (?,?,CAST(? AS date),?,NULL,0,'DI_LAM','CHUA_XAC_NHAN',?)
                """, Long.class, employee, scheduleId, Timestamp.valueOf(s.plannedStart()), Timestamp.valueOf(at),
                accountId);
        if (id == null)
            throw new IllegalStateException("Insert attendance returned no id");
        audit(accountId, id, "ATTENDANCE_SELF_CHECK_IN", null, "{\"actual_start\":\"" + at + "\"}");
        return new ClockEvent(id, at, false);
    }

    @Transactional("scheduleTransactionManager")
    public ClockEvent checkOut(long accountId, long scheduleId) {
        long employee = requireEmployee(accountId);
        lockEmployee(employee);
        Schedule s = requireSchedule(scheduleId, employee);
        Existing e = existing(scheduleId);
        if (e == null || e.in() == null)
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Check-in required before checkout");
        if (e.out() != null)
            return new ClockEvent(e.id(), e.out(), true);
        LocalDateTime at = now();
        if (!AttendanceClockPolicy.checkOut(s.plannedStart(), s.plannedEnd(), e.in(), at).allowed())
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Outside check-out window");
        Long collisions = db.queryForObject(
                """
                        SELECT COUNT_BIG(*) FROM dbo.attendance_records WITH (UPDLOCK,HOLDLOCK)
                        WHERE employee_id=? AND invalidated_at IS NULL AND attendance_id<>? AND actual_start IS NOT NULL AND actual_end IS NOT NULL
                          AND actual_start<? AND actual_end>?
                        """,
                Long.class, employee, e.id(), Timestamp.valueOf(at), Timestamp.valueOf(e.in()));
        if (collisions != null && collisions > 0)
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Overlapping confirmed time interval");
        int changed = db.update(
                """
                        UPDATE dbo.attendance_records SET actual_end=?, updated_by=?,updated_at=SYSUTCDATETIME()
                        WHERE attendance_id=? AND invalidated_at IS NULL AND actual_end IS NULL AND verification_status='CHUA_XAC_NHAN'
                        """,
                Timestamp.valueOf(at), accountId, e.id());
        if (changed != 1)
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Attendance changed during checkout");
        audit(accountId, e.id(), "ATTENDANCE_SELF_CHECK_OUT", null, "{\"actual_end\":\"" + at + "\"}");
        return new ClockEvent(e.id(), at, false);
    }

    private void lockEmployee(long employeeId) {
        // SQL Server transaction-scoped application lock, shared across clock requests.
        Integer status = db.queryForObject("""
                DECLARE @res int;
                EXEC @res = sp_getapplock @Resource=?, @LockMode='Exclusive',
                     @LockOwner='Transaction', @LockTimeout=10000;
                SELECT @res;
                """, Integer.class, "ATTENDANCE_EMPLOYEE_" + employeeId);
        if (status == null || status < 0)
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Employee attendance is busy");
    }
}