package com.hrm.service;

import com.hrm.security.AuthenticatedAccount;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class OverviewAttendanceService {
    public record Status(LocalDate workDate, long present, long absent, long leave, long missing) {}

    private final JdbcTemplate db;
    private final OvertimePermissionService identities;
    private final AttendancePermissionService permissions;
    private final Clock clock;

    public OverviewAttendanceService(JdbcTemplate db, OvertimePermissionService identities,
            AttendancePermissionService permissions, @Qualifier("hrmBusinessClock") Clock clock) {
        this.db = db;
        this.identities = identities;
        this.permissions = permissions;
        this.clock = clock;
    }

    public Status get(AuthenticatedAccount principal) {
        return get(principal, null);
    }

    public Status get(AuthenticatedAccount principal, LocalDate date) {
        var actor = identities.active(principal);
        boolean admin = "ADMIN".equals(actor.roleCode());
        if (!admin && !"HR".equals(actor.roleCode())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        }
        permissions.requirePermission(actor.accountId(), AttendancePermissionService.MANAGE);
        LocalDateTime now = LocalDateTime.now(clock);
        LocalDate day = date == null ? now.toLocalDate() : date;
        if (day.isAfter(now.toLocalDate())) throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
        return db.queryForObject("""
            WITH daily_status AS (
              SELECT CASE
                WHEN EXISTS (
                  SELECT 1 FROM dbo.employee_requests r
                  JOIN dbo.leave_request_details l ON l.request_id=r.request_id
                  WHERE r.employee_id=e.employee_id AND r.request_type='NGHI_PHEP' AND r.status='DA_DUYET'
                    AND l.leave_start<DATEADD(day,1,CAST(? AS datetime2)) AND l.leave_end>CAST(? AS datetime2)
                    AND NOT EXISTS (SELECT 1 FROM dbo.employee_request_withdrawals w WHERE w.request_id=r.request_id)
                ) THEN 'LEAVE'
                WHEN EXISTS (
                  SELECT 1 FROM dbo.work_schedules s
                  LEFT JOIN dbo.attendance_records a ON a.schedule_id=s.schedule_id AND a.invalidated_at IS NULL
                  WHERE s.employee_id=e.employee_id AND s.work_date=? AND s.cancelled_at IS NULL
                    AND (a.attendance_result IS NULL OR a.attendance_result<>'VANG')
                    AND ((a.actual_start IS NULL AND s.planned_end<?)
                      OR (a.actual_start IS NOT NULL AND a.actual_end IS NULL AND DATEADD(hour,2,s.planned_end)<?))
                ) THEN 'MISSING'
                WHEN EXISTS (
                  SELECT 1 FROM dbo.attendance_records a JOIN dbo.work_schedules s ON s.schedule_id=a.schedule_id
                  WHERE s.employee_id=e.employee_id AND s.work_date=? AND s.cancelled_at IS NULL
                    AND a.invalidated_at IS NULL AND a.attendance_result='DI_LAM' AND a.actual_start IS NOT NULL
                ) THEN 'PRESENT'
                WHEN EXISTS (
                  SELECT 1 FROM dbo.attendance_records a JOIN dbo.work_schedules s ON s.schedule_id=a.schedule_id
                  WHERE s.employee_id=e.employee_id AND s.work_date=? AND s.cancelled_at IS NULL
                    AND a.invalidated_at IS NULL AND a.attendance_result='VANG' AND a.verification_status='DA_XAC_NHAN'
                ) THEN 'ABSENT'
              END status
              FROM dbo.employees e
              WHERE e.employment_status='DANG_LAM'
                AND (?=1 OR (e.employee_id<>? AND EXISTS (
                  SELECT 1 FROM dbo.employee_assignments ea
                  JOIN dbo.hr_department_scopes hs ON hs.department_id=ea.department_id
                  WHERE ea.employee_id=e.employee_id AND ea.effective_from<=?
                    AND (ea.effective_to IS NULL OR ea.effective_to>?)
                    AND hs.hr_account_id=? AND hs.revoked_at IS NULL
                    AND hs.effective_from<=SYSUTCDATETIME()
                    AND (hs.effective_to IS NULL OR hs.effective_to>SYSUTCDATETIME())
                )))
            )
            SELECT COUNT_BIG(CASE WHEN status='PRESENT' THEN 1 END),
                   COUNT_BIG(CASE WHEN status='ABSENT' THEN 1 END),
                   COUNT_BIG(CASE WHEN status='LEAVE' THEN 1 END),
                   COUNT_BIG(CASE WHEN status='MISSING' THEN 1 END)
            FROM daily_status
            """, (rs, row) -> new Status(day, rs.getLong(1), rs.getLong(2), rs.getLong(3), rs.getLong(4)),
            day, day, day, now, now, day, day, admin ? 1 : 0, principal.employeeId(), day, day, actor.accountId());
    }
}
