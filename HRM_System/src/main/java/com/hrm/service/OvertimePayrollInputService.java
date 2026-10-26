package com.hrm.service;

import static com.hrm.dto.OvertimeDTOs.*;

import com.hrm.security.AuthenticatedAccount;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

/**
 * Read-only: OT minutes that are eligible as payroll INPUT for a payroll
 * period.
 * Writes nothing and computes no money (task scope).
 *
 * Eligible = status DA_DUYET + confirmed (actual time) + not cancelled + valid
 * minutes from the per-day allocation (so no minute is counted twice,
 * OT-20/26).
 * Overflow minutes are never included. The period is [period_start,
 * period_end).
 * Access: ADMIN (all) or HR (EMPLOYEE accounts inside their department scope).
 */
@Service
public class OvertimePayrollInputService {
    private final JdbcTemplate db;
    private final OvertimePermissionService permissions;
    private final OvertimeDailyLedger ledger;

    public OvertimePayrollInputService(JdbcTemplate db, OvertimePermissionService permissions,
            OvertimeDailyLedger ledger) {
        this.db = db;
        this.permissions = permissions;
        this.ledger = ledger;
    }

    public PayrollInputs forPeriod(AuthenticatedAccount principal, long periodId) {
        var actor = permissions.requirePayrollRead(principal);
        boolean admin = "ADMIN".equals(actor.roleCode());
        if (!admin && !"HR".equals(actor.roleCode()))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "OT payroll input access denied");

        var periods = db.query("SELECT period_code,period_start,period_end FROM dbo.payroll_periods WHERE period_id=?",
                (rs, i) -> new Object[] { rs.getString(1), rs.getObject(2, LocalDate.class),
                        rs.getObject(3, LocalDate.class) },
                periodId);
        if (periods.size() != 1)
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Payroll period not found");
        String code = (String) periods.get(0)[0];
        LocalDate start = (LocalDate) periods.get(0)[1];
        LocalDate end = (LocalDate) periods.get(0)[2];

        // Confirmed requests may cross midnight. Compute each calendar day from
        // persisted
        // detail intervals; fail the export instead of silently dropping problematic
        // OT.
        StringBuilder sql = new StringBuilder("""
                SELECT o.employee_id,e.employee_code,o.overtime_id,o.request_code,
                       o.actual_start,o.actual_end,o.actual_break_minutes,
                       o.requested_start,o.requested_end
                FROM dbo.overtime_requests o
                JOIN dbo.employees e ON e.employee_id=o.employee_id
                WHERE EXISTS (SELECT 1 FROM dbo.accounts acct JOIN dbo.roles rr
                    ON rr.role_id=acct.role_id WHERE acct.employee_id=o.employee_id
                    AND rr.role_code IN ('EMPLOYEE','HR'))
                  AND o.status='DA_DUYET' AND o.confirmed_by IS NOT NULL AND o.confirmed_at IS NOT NULL
                  AND o.actual_start<? AND o.actual_end>?
                """);
        List<Object> args = new ArrayList<>(List.of(end.atStartOfDay(), start.atStartOfDay()));
        if (!admin) {
            sql.append(" AND ").append(OvertimePermissionService.hrScopeExistsSql(
                    "o.employee_id", "CONVERT(date,o.actual_start)"));
            args.add(actor.accountId());
        }
        sql.append(" ORDER BY e.employee_code,o.actual_start,o.overtime_id");
        List<PayrollInputRow> rows = new ArrayList<>();
        db.query(sql.toString(), rs -> {
            long emp = rs.getLong(1), otId = rs.getLong(3);
            String empCode = rs.getString(2), requestCode = rs.getString(4);
            var actualStart = rs.getObject(5, java.time.LocalDateTime.class);
            var actualEnd = rs.getObject(6, java.time.LocalDateTime.class);
            int breaks = rs.getInt(7);
            var approvedStart = rs.getObject(8, java.time.LocalDateTime.class);
            var approvedEnd = rs.getObject(9, java.time.LocalDateTime.class);
            if (actualStart.isBefore(approvedStart) || actualEnd.isAfter(approvedEnd))
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "Confirmed OT exceeds approved range: " + otId);
            ledger.netMinutes(otId, actualStart, actualEnd, breaks).forEach((day, minutes) -> {
                if (!day.isBefore(start) && day.isBefore(end) && minutes > 0)
                    rows.add(new PayrollInputRow(emp, empCode, otId, requestCode, day, Math.toIntExact(minutes)));
            });
        }, args.toArray());
        Map<Long, long[]> sums = new LinkedHashMap<>();
        Map<Long, String> codes = new LinkedHashMap<>();
        for (PayrollInputRow r : rows) {
            sums.computeIfAbsent(r.employeeId(), k -> new long[1])[0] += r.validMinutes();
            codes.putIfAbsent(r.employeeId(), r.employeeCode());
        }
        List<PayrollInputTotal> totals = new ArrayList<>();
        sums.forEach((emp, v) -> totals.add(new PayrollInputTotal(emp, codes.get(emp), v[0])));
        return new PayrollInputs(periodId, code, start, end, totals, rows);
    }
}
