package com.hrm.service;

import com.hrm.security.AuthenticatedAccount;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

/**
 * Read-only summary: only active, confirmed, non-invalidated attendance.
 * Cross-midnight attendance carrying only an aggregate break count is reported
 * as unresolved, never assigned arbitrary daily hours.
 */
@Service
public class ConfirmedAttendancePeriodService {
    public record EmployeeDay(long employeeId, LocalDate workDate, long presentRecords,
            long absentRecords, long netMinutes, long unresolvedRecords) {
    }

    public record PeriodSummary(LocalDate from, LocalDate to, List<EmployeeDay> days,
            long netMinutes, long unresolvedRecords) {
    }

    private final JdbcTemplate db;

    public ConfirmedAttendancePeriodService(JdbcTemplate db) {
        this.db = db;
    }

    public PeriodSummary summarize(AuthenticatedAccount principal, LocalDate from, LocalDate to,
            Long employeeFilter) {
        if (principal == null || principal.accountId() == null || principal.accountId() <= 0)
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required");
        if (from == null || to == null || to.isBefore(from) || ChronoUnit.DAYS.between(from, to) > 366)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid period");
        if (employeeFilter != null && employeeFilter <= 0)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid employee");
        var actors = db.query("""
                SELECT a.account_id,a.employee_id,r.role_code FROM dbo.accounts a
                JOIN dbo.roles r ON r.role_id=a.role_id
                WHERE a.account_id=? AND a.is_active=1
                """, (rs, n) -> new Object[] { rs.getLong(1), rs.getObject(2, Long.class), rs.getString(3) },
                principal.accountId());
        if (actors.size() != 1 || !Objects.equals(actors.get(0)[1], principal.employeeId())
                || !Objects.equals(actors.get(0)[2], principal.roleCode()))
            deny();
        String role = (String) actors.get(0)[2];
        if (!"ADMIN".equals(role) && !"HR".equals(role))
            deny();
        Long granted = db.queryForObject("""
                SELECT COUNT_BIG(*) FROM dbo.accounts a JOIN dbo.role_permissions rp ON rp.role_id=a.role_id
                JOIN dbo.permissions p ON p.permission_id=rp.permission_id
                WHERE a.account_id=? AND a.is_active=1 AND p.permission_code='ATTENDANCE_MANAGE'
                """, Long.class, principal.accountId());
        if (granted == null || granted != 1L)
            deny();
        StringBuilder query = new StringBuilder("""
                SELECT a.employee_id,a.work_date,a.attendance_result,a.actual_start,a.actual_end,
                       a.actual_break_minutes
                FROM dbo.attendance_records a
                WHERE a.work_date BETWEEN ? AND ?
                  AND a.verification_status='DA_XAC_NHAN'
                  AND a.invalidated_at IS NULL
                """);
        List<Object> parameters = new ArrayList<>();
        parameters.add(from);
        parameters.add(to);
        if (employeeFilter != null) {
            query.append(" AND a.employee_id=?");
            parameters.add(employeeFilter);
        }
        if ("HR".equals(role)) {
            query.append("""
                    AND EXISTS (
                      SELECT 1 FROM dbo.accounts owner JOIN dbo.roles owner_role ON owner_role.role_id=owner.role_id
                      WHERE owner.employee_id=a.employee_id AND owner.is_active=1
                        AND owner_role.role_code='EMPLOYEE'
                    )
                    AND EXISTS (
                      SELECT 1 FROM dbo.employee_assignments ea
                      JOIN dbo.hr_department_scopes hs ON hs.department_id=ea.department_id
                      WHERE ea.employee_id=a.employee_id AND ea.effective_from<=a.work_date
                        AND (ea.effective_to IS NULL OR ea.effective_to>a.work_date)
                        AND hs.hr_account_id=? AND hs.revoked_at IS NULL
                        AND hs.effective_from<=SYSUTCDATETIME()
                        AND (hs.effective_to IS NULL OR hs.effective_to>SYSUTCDATETIME())
                    )
                    """);
            parameters.add(principal.accountId());
        }
        query.append(" ORDER BY a.employee_id,a.work_date,a.actual_start");
        record Line(long employeeId, LocalDate date, String result, LocalDateTime start,
                LocalDateTime end, int breaks) {
        }
        var rows = db.query(query.toString(), (rs, n) -> new Line(rs.getLong(1), rs.getDate(2).toLocalDate(),
                rs.getString(3), rs.getObject(4, LocalDateTime.class), rs.getObject(5, LocalDateTime.class),
                rs.getInt(6)),
                parameters.toArray());
        record Key(long employee, LocalDate date) {
        }
        class Total {
            long present, absent, net, unresolved;
        }
        java.util.Map<Key, Total> totals = new java.util.LinkedHashMap<>();
        for (Line line : rows) {
            var t = totals.computeIfAbsent(new Key(line.employeeId(), line.date()), k -> new Total());
            if ("VANG".equals(line.result())) {
                t.absent++;
                continue;
            }
            if (!"DI_LAM".equals(line.result())) {
                t.unresolved++;
                continue;
            }
            t.present++;
            if (line.start() == null || line.end() == null || !line.end().isAfter(line.start())
                    || line.breaks() < 0
                    || !line.start().toLocalDate().equals(line.end().minusNanos(1).toLocalDate())) {
                t.unresolved++;
                continue;
            }
            long minutes = ChronoUnit.MINUTES.between(line.start(), line.end()) - line.breaks();
            if (minutes < 0) {
                t.unresolved++;
                continue;
            }
            t.net += minutes;
        }
        List<EmployeeDay> days = new ArrayList<>();
        long net = 0, unresolved = 0;
        for (var entry : totals.entrySet()) {
            var k = entry.getKey();
            var t = entry.getValue();
            days.add(new EmployeeDay(k.employee(), k.date(), t.present, t.absent, t.net, t.unresolved));
            net += t.net;
            unresolved += t.unresolved;
        }
        return new PeriodSummary(from, to, List.copyOf(days), net, unresolved);
    }

    private static void deny() {
        throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Attendance report denied");
    }
}
