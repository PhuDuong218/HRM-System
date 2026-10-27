package com.hrm.service;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Read-only internal discovery of missing check-outs after the allowed
 * deadline.
 * Does not mark attendance invalid, edit recorded times, send notifications, or
 * expose employee data through a public API. Caller must enforce HR/ADMIN RBAC
 * and historical department scope before presenting any returned records.
 */
@Service
public class AttendanceMissingCheckoutQueryService {
    public record ExpiredCheckout(long attendanceId, long employeeId, long scheduleId,
            LocalDateTime scheduledEnd, LocalDateTime recordedCheckIn,
            LocalDateTime checkoutDeadline) {
    }

    private final JdbcTemplate jdbc;
    private final Clock clock;

    @Autowired
    public AttendanceMissingCheckoutQueryService(JdbcTemplate jdbc) {
        this(jdbc, Clock.systemDefaultZone());
    }

    // Package-visible for test injection of the server clock.
    AttendanceMissingCheckoutQueryService(JdbcTemplate jdbc, Clock clock) {
        this.jdbc = jdbc;
        this.clock = clock;
    }

    @Transactional(value = "scheduleTransactionManager", readOnly = true)
    public List<ExpiredCheckout> findExpiredCheckouts(int limit) {
        if (limit < 1 || limit > 1000) {
            throw new IllegalArgumentException("limit must be between 1 and 1000");
        }
        LocalDateTime now = LocalDateTime.now(clock);
        LocalDateTime maximumEnd = now.minusHours(2);
        // Strict boundary: at exactly end+2h the employee still may check out.
        // work_schedules.schedule_id/employee_id are joined to the attendance row
        // so an inconsistent orphan cannot be treated as valid evidence.
        return jdbc.query("""
                SELECT TOP (?) ar.attendance_id, ar.employee_id, ws.schedule_id,
                       ws.planned_end, ar.actual_start
                FROM dbo.attendance_records ar
                INNER JOIN dbo.work_schedules ws
                  ON ws.schedule_id = ar.schedule_id AND ws.employee_id = ar.employee_id
                WHERE ar.invalidated_at IS NULL AND ar.actual_start IS NOT NULL
                  AND ar.actual_end IS NULL
                  AND ws.planned_end < ?
                ORDER BY ws.planned_end, ar.attendance_id
                """, (rs, index) -> {
            LocalDateTime end = rs.getTimestamp(4).toLocalDateTime();
            LocalDateTime in = rs.getTimestamp(5).toLocalDateTime();
            return new ExpiredCheckout(rs.getLong(1), rs.getLong(2), rs.getLong(3),
                    end, in, end.plusHours(2));
        }, limit, Timestamp.valueOf(maximumEnd));
    }
}
