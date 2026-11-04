package com.hrm.service;

import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.ArrayList;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * Raw attendance records for authorized management users; no validity
 * inference.
 */
@Service
public class AttendanceManagementReadOnlyQueryService {
    public record Item(long attendanceId, long employeeId, Long scheduleId, LocalDate workDate,
            LocalDateTime actualStart, LocalDateTime actualEnd, int actualBreakMinutes,
            String attendanceResult, String verificationStatus,
            Long confirmedBy, LocalDateTime confirmedAt) {
    }

    public record Page(List<Item> items, int page, int size, long totalElements, long totalPages,
            String dataMeaning) {
    }

    private final JdbcTemplate db;
    private final AttendancePermissionService permissions;

    public AttendanceManagementReadOnlyQueryService(JdbcTemplate db,
            AttendancePermissionService permissions) {
        this.db = db;
        this.permissions = permissions;
    }

    private static LocalDateTime dt(Timestamp v) {
        return v == null ? null : v.toLocalDateTime();
    }

    @Transactional(value = "scheduleTransactionManager", readOnly = true)
    public Page search(long actorId, LocalDate from, LocalDate to, Long employeeId,
            String verificationStatus, int page, int size) {
        if (from == null || to == null || from.isAfter(to) || to.isAfter(from.plusDays(366))
                || (employeeId != null && employeeId <= 0) || page < 0 || size < 1 || size > 100
                || (long) page * size > Integer.MAX_VALUE
                || (verificationStatus != null && !verificationStatus.equals("CHUA_XAC_NHAN")
                        && !verificationStatus.equals("DA_XAC_NHAN"))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid attendance query");
        }
        AttendancePermissionService.Actor actor = permissions.requirePermission(
                actorId, AttendancePermissionService.MANAGE);
        // The database does not expose invalidated_at: this endpoint intentionally
        // returns raw records.
        // An HR can only read employees with active EMPLOYEE accounts AND a historical
        // department
        // assignment matching their *currently valid* management scope; no self
        // attendance.
        String where = """
                FROM dbo.attendance_records ar
                WHERE ar.work_date >= ? AND ar.work_date <= ?
                  AND (
                       ? = 'ADMIN'
                       OR (
                           ? = 'HR'
                           AND ar.employee_id <> ?
                           AND EXISTS (
                               SELECT 1 FROM dbo.accounts ea
                               JOIN dbo.roles er ON er.role_id = ea.role_id
                               WHERE ea.employee_id = ar.employee_id
                                 AND ea.is_active = 1 AND er.role_code = 'EMPLOYEE'
                           )
                           AND EXISTS (
                               SELECT 1
                               FROM dbo.employee_assignments assn
                               JOIN dbo.hr_department_scopes hs
                                 ON hs.department_id = assn.department_id
                               WHERE assn.employee_id = ar.employee_id
                                 AND assn.effective_from <= ar.work_date
                                 AND (assn.effective_to IS NULL OR assn.effective_to > ar.work_date)
                                 AND hs.hr_account_id = ?
                                 AND hs.revoked_at IS NULL
                                 AND hs.effective_from <= SYSUTCDATETIME()
                                 AND (hs.effective_to IS NULL OR hs.effective_to > SYSUTCDATETIME())
                           )
                       )
                  )
                """;
        Long actorEmployeeId = actor.employeeId();
        // ADMIN's employee ID is unused; HR's ID must be present (enforced in
        // requirePermission).
        long ownEmployeeId = actorEmployeeId == null ? -1 : actorEmployeeId;
        // Optional filters are appended as concrete predicates to avoid untyped NULL
        // parameters on SQL Server.
        StringBuilder filter = new StringBuilder(where);
        List<Object> params = new ArrayList<>(List.of(from, to, actor.roleCode(),
                actor.roleCode(), ownEmployeeId, actorId));
        if (employeeId != null) {
            filter.append(" AND ar.employee_id = ?");
            params.add(employeeId);
        }
        if (verificationStatus != null) {
            filter.append(" AND ar.verification_status = ?");
            params.add(verificationStatus);
        }
        String filtered = filter.toString();
        Object[] args = params.toArray();
        Long count = db.queryForObject("SELECT COUNT_BIG(*) " + filtered, Long.class, args);
        long total = count == null ? 0 : count;
        Object[] pagedArgs = java.util.Arrays.copyOf(args, args.length + 2);
        pagedArgs[args.length] = page * size;
        pagedArgs[args.length + 1] = size;
        List<Item> items = db.query(
                """
                        SELECT ar.attendance_id, ar.employee_id, ar.schedule_id, ar.work_date,
                               ar.actual_start, ar.actual_end, ar.actual_break_minutes,
                               ar.attendance_result, ar.verification_status, ar.confirmed_by, ar.confirmed_at
                        """ + filtered
                        + " ORDER BY ar.work_date DESC, ar.attendance_id DESC OFFSET ? ROWS FETCH NEXT ? ROWS ONLY",
                (rs, index) -> new Item(rs.getLong("attendance_id"), rs.getLong("employee_id"),
                        rs.getObject("schedule_id", Long.class), rs.getDate("work_date").toLocalDate(),
                        dt(rs.getTimestamp("actual_start")), dt(rs.getTimestamp("actual_end")),
                        rs.getInt("actual_break_minutes"), rs.getString("attendance_result"),
                        rs.getString("verification_status"), rs.getObject("confirmed_by", Long.class),
                        dt(rs.getTimestamp("confirmed_at"))),
                pagedArgs);
        return new Page(items, page, size, total, (total + size - 1) / size,
                "RAW_RECORDS_NOT_VALIDITY_VERIFIED");
    }
}
