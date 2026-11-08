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
            long absentRecords, long netMinutes, long unresolvedRecords, String employeeCode, String employeeName, String department) {
    }

    public record PeriodSummary(LocalDate from, LocalDate to, List<EmployeeDay> days,
            long netMinutes, long unresolvedRecords) {
    }

    private final JdbcTemplate db;
    private final AttendanceActualBreakService actualBreaks;

    public ConfirmedAttendancePeriodService(JdbcTemplate db,AttendanceActualBreakService actualBreaks) {
        this.actualBreaks=actualBreaks;
        this.db = db;
    }

    @org.springframework.transaction.annotation.Transactional(value="scheduleTransactionManager",readOnly=true)
    public PeriodSummary summarize(AuthenticatedAccount principal, LocalDate from, LocalDate to,
            Long employeeFilter) {
        return summarizeInternal(principal, from, to, employeeFilter, false);
    }

    @org.springframework.transaction.annotation.Transactional(value="scheduleTransactionManager",readOnly=true)
    public PeriodSummary summarizeMine(AuthenticatedAccount principal, LocalDate from, LocalDate to) {
        return summarizeInternal(principal, from, to, null, true);
    }

    private PeriodSummary summarizeInternal(AuthenticatedAccount principal, LocalDate from, LocalDate to,
            Long employeeFilter, boolean mine) {
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
        if (mine) {
            if ((!"EMPLOYEE".equals(role) && !"HR".equals(role)) || principal.employeeId() == null || principal.employeeId() <= 0) deny();
            employeeFilter = principal.employeeId();
        }
        if (!mine && !"ADMIN".equals(role) && !"HR".equals(role))
            deny();
        if (!mine) {
        Long granted = db.queryForObject("""
                SELECT COUNT_BIG(*) FROM dbo.accounts a JOIN dbo.role_permissions rp ON rp.role_id=a.role_id
                JOIN dbo.permissions p ON p.permission_id=rp.permission_id
                WHERE a.account_id=? AND a.is_active=1 AND p.permission_code='ATTENDANCE_MANAGE'
                """, Long.class, principal.accountId());
        if (granted == null || granted != 1L)
            deny();
        }
        StringBuilder query = new StringBuilder("""
                SELECT a.employee_id,a.work_date,a.attendance_result,a.actual_start,a.actual_end,
                       a.actual_break_minutes,e.employee_code,e.full_name,
                       (SELECT TOP(1) d.department_name FROM dbo.employee_assignments ea
                        JOIN dbo.departments d ON d.department_id=ea.department_id
                        WHERE ea.employee_id=a.employee_id AND ea.effective_from<=a.work_date
                          AND (ea.effective_to IS NULL OR ea.effective_to>a.work_date)
                        ORDER BY ea.effective_from DESC,ea.department_id) department_name,a.attendance_id
                FROM dbo.attendance_records a JOIN dbo.employees e ON e.employee_id=a.employee_id
                WHERE (a.work_date BETWEEN ? AND ? OR (a.actual_start<? AND a.actual_end>?))
                  AND a.verification_status='DA_XAC_NHAN'
                  AND a.invalidated_at IS NULL
                """);
        List<Object> parameters = new ArrayList<>();
        parameters.add(from);
        parameters.add(to);
        parameters.add(to.plusDays(1).atStartOfDay());
        parameters.add(from.atStartOfDay());
        if (employeeFilter != null) {
            query.append(" AND a.employee_id=?");
            parameters.add(employeeFilter);
        }
        if (!mine && "HR".equals(role)) {
            query.append("""
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
                LocalDateTime end, Integer breaks, String code, String name, String department,long attendanceId) {
        }
        var rows = db.query(query.toString(), (rs, n) -> new Line(rs.getLong(1), rs.getDate(2).toLocalDate(),
                rs.getString(3), rs.getObject(4, LocalDateTime.class), rs.getObject(5, LocalDateTime.class),
                rs.getObject(6, Integer.class), rs.getString(7), rs.getString(8), rs.getString(9),rs.getLong(10)),
                parameters.toArray());
        record Key(long employee, LocalDate date) {
        }
        class Total {
            long present, absent, net, unresolved; String code, name, department;
        }
        java.util.Map<Key, Total> totals = new java.util.LinkedHashMap<>();
        var verifiedBreaks=actualBreaks.verifiedMany(rows.stream().map(line->new AttendanceActualBreakService.Source(line.attendanceId(),line.start(),line.end(),line.breaks())).toList());
        for (Line line : rows) {
            java.util.function.Function<LocalDate,Total> at=day->{var t=totals.computeIfAbsent(new Key(line.employeeId(),day),k->new Total());t.code=line.code();t.name=line.name();t.department=line.department();return t;};
            boolean anchored=!line.date().isBefore(from)&&!line.date().isAfter(to);
            LocalDate unresolvedDay=anchored?line.date():line.start()==null?from:line.start().toLocalDate().isBefore(from)?from:line.start().toLocalDate();
            if("VANG".equals(line.result())){if(anchored)at.apply(line.date()).absent++;continue;}
            if(!"DI_LAM".equals(line.result())){at.apply(unresolvedDay).unresolved++;continue;}
            if(anchored)at.apply(line.date()).present++;
            try {
                var details=verifiedBreaks.get(line.attendanceId());
                var allocation=AttendanceDayAllocation.allocate(line.start(),line.end(),line.breaks(),details);
                allocation.forEach((day,minutes)->{if(!day.isBefore(from)&&!day.isAfter(to))at.apply(day).net+=minutes;});
            } catch(ResponseStatusException ex) {at.apply(unresolvedDay).unresolved++;}
        }
        List<EmployeeDay> days = new ArrayList<>();
        long net = 0, unresolved = 0;
        for (var entry : totals.entrySet()) {
            var k = entry.getKey();
            var t = entry.getValue();
            days.add(new EmployeeDay(k.employee(), k.date(), t.present, t.absent, t.net, t.unresolved, t.code, t.name, t.department));
            net += t.net;
            unresolved += t.unresolved;
        }
        return new PeriodSummary(from, to, List.copyOf(days), net, unresolved);
    }

    private static void deny() {
        throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Attendance report denied");
    }
}
