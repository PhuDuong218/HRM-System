package com.hrm.service;

import com.hrm.security.AuthenticatedAccount;
import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class OverviewTrendsService {
    public record Month(String month, long overtime, long late, long hired, long terminated) {}

    private final NamedParameterJdbcTemplate db;
    private final OvertimePermissionService identities;
    private final AttendancePermissionService permissions;
    private final Clock clock;

    public OverviewTrendsService(NamedParameterJdbcTemplate db, OvertimePermissionService identities,
            AttendancePermissionService permissions, @Qualifier("hrmBusinessClock") Clock clock) {
        this.db = db;
        this.identities = identities;
        this.permissions = permissions;
        this.clock = clock;
    }

    public List<Month> get(AuthenticatedAccount principal, LocalDate date) {
        var actor = identities.active(principal);
        boolean admin = "ADMIN".equals(actor.roleCode());
        if (!admin && !"HR".equals(actor.roleCode())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        }
        permissions.requirePermission(actor.accountId(), AttendancePermissionService.MANAGE);
        LocalDate currentDay = LocalDate.now(clock);
        LocalDate today = date == null ? currentDay : date;
        if (today.isAfter(currentDay) || today.getYear() < 2)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
        YearMonth first = YearMonth.from(today).minusMonths(5);
        Map<String, Month> months = new LinkedHashMap<>();
        for (int index = 0; index < 6; index++) {
            String month = first.plusMonths(index).toString();
            months.put(month, new Month(month, 0, 0, 0, 0));
        }

        var rows = db.query("""
            WITH events AS (
                SELECT employee_id, CAST(actual_start AS date) AS event_day, 'OT' AS kind
                FROM dbo.overtime_requests WHERE status='DA_DUYET' AND confirmed_at IS NOT NULL
                UNION ALL
                SELECT s.employee_id, s.work_date, 'LATE'
                FROM dbo.work_schedules s JOIN dbo.attendance_records a ON a.schedule_id=s.schedule_id
                WHERE s.cancelled_at IS NULL AND a.invalidated_at IS NULL AND a.attendance_result='DI_LAM'
                  AND a.actual_start > DATEADD(minute,15,s.planned_start)
                UNION ALL
                SELECT employee_id, hire_date, 'HIRE' FROM dbo.employees
                UNION ALL
                SELECT employee_id, termination_date, 'EXIT' FROM dbo.employees
            )
            SELECT CONVERT(char(7), ev.event_day, 126),
                   COUNT_BIG(CASE WHEN ev.kind='OT' THEN 1 END),
                   COUNT_BIG(CASE WHEN ev.kind='LATE' THEN 1 END),
                   COUNT_BIG(CASE WHEN ev.kind='HIRE' THEN 1 END),
                   COUNT_BIG(CASE WHEN ev.kind='EXIT' THEN 1 END)
            FROM events ev
            WHERE ev.event_day BETWEEN :firstDay AND :today
              AND (:admin=1 OR (ev.employee_id<>:employeeId AND EXISTS (
                SELECT 1 FROM dbo.employee_assignments ea
                JOIN dbo.hr_department_scopes hs ON hs.department_id=ea.department_id
                WHERE ea.employee_id=ev.employee_id AND ea.effective_from<=ev.event_day
                  AND (ea.effective_to IS NULL OR ea.effective_to>=ev.event_day
                    AND (ev.kind='EXIT' OR ea.effective_to>ev.event_day))
                  AND hs.hr_account_id=:accountId AND hs.revoked_at IS NULL
                  AND hs.effective_from<=SYSUTCDATETIME()
                  AND (hs.effective_to IS NULL OR hs.effective_to>SYSUTCDATETIME())
              )))
            GROUP BY CONVERT(char(7), ev.event_day, 126)
            """, Map.of("firstDay", first.atDay(1), "today", today, "admin", admin ? 1 : 0,
                    "employeeId", principal.employeeId(), "accountId", actor.accountId()),
            (rs, row) -> new Month(rs.getString(1), rs.getLong(2), rs.getLong(3), rs.getLong(4), rs.getLong(5)));
        rows.forEach(row -> months.put(row.month(), row));
        return List.copyOf(months.values());
    }
}
