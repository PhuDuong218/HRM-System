package com.hrm.service;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * Read-only, employee-scoped anomaly view. The person being queried must be
 * supplied explicitly; service checks current actor RBAC and historical
 * department authorization separately for each work date returned.
 * No persistent status, notification or correction is created.
 */
@Service
public class AttendanceAnomalyQueryService {
    public record AnomalyView(long attendanceId, long employeeId, long scheduleId,
            LocalDate workDate, LocalDateTime plannedStart, LocalDateTime plannedEnd,
            LocalDateTime actualStart, LocalDateTime actualEnd,
            Set<AttendanceAnomalyPolicy.Anomaly> anomalies) {
    }

    private record Raw(long attendanceId, long employeeId, long scheduleId,
            LocalDate workDate, LocalDateTime plannedStart, LocalDateTime plannedEnd,
            LocalDateTime actualStart, LocalDateTime actualEnd) {
    }

    private final JdbcTemplate db;
    private final AttendancePermissionService permission;
    private final Clock clock;

    @Autowired
    public AttendanceAnomalyQueryService(JdbcTemplate db, AttendancePermissionService permission) {
        this(db, permission, Clock.systemDefaultZone());
    }

    AttendanceAnomalyQueryService(JdbcTemplate db, AttendancePermissionService permission, Clock clock) {
        this.db = db;
        this.permission = permission;
        this.clock = clock;
    }

    private static LocalDateTime dt(Timestamp t) {
        return t == null ? null : t.toLocalDateTime();
    }

    @Transactional(value = "scheduleTransactionManager", readOnly = true)
    public List<AnomalyView> findForEmployee(long actorAccountId, long employeeId,
            LocalDate from, LocalDate to, int limit) {
        if (employeeId <= 0 || from == null || to == null || from.isAfter(to)
                || from.plusDays(31).isBefore(to) || limit < 1 || limit > 100) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid employee, range, or limit");
        }
        // Check actor can manage before reading anything; no direct calls bypass RBAC.
        permission.requirePermission(actorAccountId, AttendancePermissionService.MANAGE);
        List<Raw> rows = db.query("""
                SELECT TOP (?) a.attendance_id, a.employee_id, a.schedule_id, a.work_date,
                       w.planned_start, w.planned_end, a.actual_start, a.actual_end
                FROM dbo.attendance_records a
                JOIN dbo.work_schedules w ON w.schedule_id=a.schedule_id
                                     AND w.employee_id=a.employee_id
                WHERE a.employee_id=? AND a.invalidated_at IS NULL AND a.work_date>=? AND a.work_date<=?
                ORDER BY a.work_date DESC, a.attendance_id DESC
                """, (rs, n) -> new Raw(rs.getLong(1), rs.getLong(2), rs.getLong(3),
                rs.getDate(4).toLocalDate(), dt(rs.getTimestamp(5)), dt(rs.getTimestamp(6)),
                dt(rs.getTimestamp(7)), dt(rs.getTimestamp(8))),
                limit, employeeId, java.sql.Date.valueOf(from), java.sql.Date.valueOf(to));
        List<AnomalyView> result = new ArrayList<>();
        LocalDateTime now = LocalDateTime.now(clock);
        for (Raw r : rows) {
            // Fail closed: missing assignment history/role is a denial, not an excuse
            // to fall back to the current department.
            permission.requireAttendanceAccess(actorAccountId, r.employeeId(),
                    r.workDate(), AttendancePermissionService.MANAGE);
            var flags = AttendanceAnomalyPolicy.classify(
                    r.plannedStart(), r.plannedEnd(), r.actualStart(), r.actualEnd(), now);
            if (!flags.isEmpty())
                result.add(new AnomalyView(r.attendanceId(), r.employeeId(),
                        r.scheduleId(), r.workDate(), r.plannedStart(), r.plannedEnd(),
                        r.actualStart(), r.actualEnd(), flags));
        }
        return List.copyOf(result);
    }
}