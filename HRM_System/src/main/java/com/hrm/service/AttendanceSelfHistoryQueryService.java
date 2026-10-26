package com.hrm.service;

import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/** Read-only attendance history for the authenticated employee only. */
@Service
public class AttendanceSelfHistoryQueryService {
    public record HistoryItem(long attendanceId, long scheduleId, LocalDate workDate,
            LocalDateTime actualStart, LocalDateTime actualEnd, int actualBreakMinutes,
            String attendanceResult, String verificationStatus) {
    }

    private final JdbcTemplate db;

    public AttendanceSelfHistoryQueryService(JdbcTemplate db) {
        this.db = db;
    }

    @Transactional(value = "scheduleTransactionManager", readOnly = true)
    public List<HistoryItem> findMine(long actorAccountId, LocalDate from, LocalDate to,
            int page, int size) {
        if (actorAccountId <= 0 || from == null || to == null || from.isAfter(to)
                || to.isAfter(from.plusDays(366)) || page < 0 || size < 1 || size > 100
                || (long) page * size > Integer.MAX_VALUE) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid attendance history query");
        }
        List<Long> employees = db.queryForList("""
                SELECT a.employee_id FROM dbo.accounts a
                JOIN dbo.roles r ON r.role_id = a.role_id
                WHERE a.account_id = ? AND a.is_active = 1
                  AND a.employee_id IS NOT NULL AND r.role_code = 'EMPLOYEE'
                """, Long.class, actorAccountId);
        if (employees.size() != 1 || employees.get(0) == null) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Employee attendance access denied");
        }
        return db.query("""
                SELECT ar.attendance_id, ar.schedule_id, ar.work_date,
                       ar.actual_start, ar.actual_end, ar.actual_break_minutes,
                       ar.attendance_result, ar.verification_status
                FROM dbo.attendance_records ar
                WHERE ar.employee_id = ? AND ar.work_date >= ? AND ar.work_date <= ?
                ORDER BY ar.work_date DESC, ar.attendance_id DESC
                OFFSET ? ROWS FETCH NEXT ? ROWS ONLY
                """, (rs, i) -> new HistoryItem(
                rs.getLong("attendance_id"), rs.getLong("schedule_id"),
                rs.getDate("work_date").toLocalDate(),
                toDateTime(rs.getTimestamp("actual_start")),
                toDateTime(rs.getTimestamp("actual_end")),
                rs.getInt("actual_break_minutes"),
                rs.getString("attendance_result"),
                rs.getString("verification_status")),
                employees.get(0), from, to, page * size, size);
    }

    private static LocalDateTime toDateTime(Timestamp v) {
        return v == null ? null : v.toLocalDateTime();
    }
}
