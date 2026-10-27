package com.hrm.service;

import com.hrm.security.AuthenticatedAccount;
import com.hrm.dto.OvertimeDTOs.BreakInput;
import java.time.*;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

/**
 * Read-only, no schema changes. Provides an accurate preview for detailed
 * breaks,
 * cross-midnight OT, excess time and daily limits. Never confirms or pays OT.
 */
@Service
public class OvertimePreviewService {
    private final JdbcTemplate db;
    private final OvertimePermissionService permissions;

    public OvertimePreviewService(JdbcTemplate db, OvertimePermissionService permissions) {
        this.db = db;
        this.permissions = permissions;
    }

    public record PreviewDay(LocalDate day, long actualNetMinutes, long approvedMinutes,
            long validMinutes, long excessMinutes, long remainingDailyCapacity) {
    }

    public record Preview(long overtimeId, boolean canConfirmUsingCurrentSchema,
            String limitation, List<PreviewDay> days) {
    }

    public Preview preview(AuthenticatedAccount principal, long overtimeId, List<BreakInput> breaks) {
        var rows = db.query("""
                SELECT employee_id,requested_start,requested_end,actual_start,actual_end,status,
                       confirmed_by,actual_break_minutes
                  FROM dbo.overtime_requests WHERE overtime_id=?
                """, (rs, i) -> new Request(rs.getLong(1), rs.getObject(2, LocalDateTime.class),
                rs.getObject(3, LocalDateTime.class), rs.getObject(4, LocalDateTime.class),
                rs.getObject(5, LocalDateTime.class), rs.getString(6), rs.getObject(7, Long.class), rs.getInt(8)),
                overtimeId);
        if (rows.isEmpty())
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "OT not found");
        Request ot = rows.get(0);
        permissions.requireView(principal, ot.employee(), ot.approvedStart().toLocalDate());
        if (!"DA_DUYET".equals(ot.status()) || ot.actualStart() == null || ot.actualEnd() == null)
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Approved OT with recorded actual is required");
        if (ot.confirmedBy() != null)
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Already confirmed; preview is for pending confirmation");
        List<OvertimeDailyCalculation.Interval> intervals = new ArrayList<>();
        if (breaks != null)
            for (BreakInput b : breaks)
                intervals.add(new OvertimeDailyCalculation.Interval(b.breakStart(), b.breakEnd()));
        long detailedMinutes = intervals.stream().mapToLong(b -> Duration.between(b.start(), b.end()).toMinutes())
                .sum();
        if (!intervals.isEmpty() && ot.breakMinutes() != detailedMinutes)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Break intervals total differs from persisted actual_break_minutes");
        if (intervals.isEmpty() && ot.breakMinutes() != 0)
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Exact break intervals are needed for safe daily allocation");

        LocalDate first = ot.actualStart().toLocalDate(), last = ot.actualEnd().minusNanos(1).toLocalDate();
        var regularIntervals = new ArrayList<OvertimeDailyCalculation.Interval>();
        var dayContext = new HashMap<LocalDate, OvertimeDailyCalculation.DayContext>();
        for (LocalDate d = first; !d.isAfter(last); d = d.plusDays(1)) {
            LocalDateTime lo = d.atStartOfDay(), hi = d.plusDays(1).atStartOfDay();
            Long unconfirmed = db.queryForObject(
                    """
                            SELECT COUNT_BIG(*) FROM dbo.work_schedules s
                            WHERE s.employee_id=? AND s.planned_start<? AND s.planned_end>?
                              AND NOT EXISTS(SELECT 1 FROM dbo.attendance_records a
                                WHERE a.schedule_id=s.schedule_id AND a.invalidated_at IS NULL AND a.verification_status='DA_XAC_NHAN')
                            """,
                    Long.class, ot.employee(), hi, lo);
            if (unconfirmed != null && unconfirmed > 0)
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Unconfirmed regular attendance for " + d);
            var attendance = db.query("""
                    SELECT actual_start,actual_end,actual_break_minutes FROM dbo.attendance_records
                    WHERE employee_id=? AND invalidated_at IS NULL AND verification_status='DA_XAC_NHAN'
                      AND attendance_result='DI_LAM' AND actual_end IS NOT NULL
                      AND actual_start<? AND actual_end>?
                    """, (rs, i) -> new Attendance(rs.getObject(1, LocalDateTime.class),
                    rs.getObject(2, LocalDateTime.class), rs.getInt(3)), ot.employee(), hi, lo);
            long regularNet = 0;
            for (Attendance a : attendance) {
                // Without precise regular break times, splitting cross-day attendance is not
                // reliable.
                if (a.start().toLocalDate().isBefore(d) || !a.end().minusNanos(1).toLocalDate().equals(d))
                    throw new ResponseStatusException(HttpStatus.CONFLICT,
                            "Cross-day regular attendance needs break allocation");
                long net = Duration.between(a.start(), a.end()).toMinutes() - a.breakMinutes();
                if (net < 0)
                    throw new ResponseStatusException(HttpStatus.CONFLICT, "Invalid regular break minutes");
                regularNet += net;
                regularIntervals.add(new OvertimeDailyCalculation.Interval(a.start(), a.end()));
            }
            var confirmed = db.query("""
                    SELECT actual_start,actual_end,actual_break_minutes FROM dbo.overtime_requests
                    WHERE employee_id=? AND overtime_id<>? AND status='DA_DUYET'
                      AND confirmed_by IS NOT NULL AND confirmed_at IS NOT NULL
                      AND actual_start<? AND actual_end>?
                    """, (rs, i) -> new Attendance(rs.getObject(1, LocalDateTime.class),
                    rs.getObject(2, LocalDateTime.class), rs.getInt(3)), ot.employee(), overtimeId, hi, lo);
            long otherNet = 0;
            for (Attendance a : confirmed) {
                if (a.start().isBefore(lo) || a.end().isAfter(hi))
                    throw new ResponseStatusException(HttpStatus.CONFLICT,
                            "Cross-day confirmed OT requires authoritative allocations");
                // Reject overlaps with other confirmed OT instead of silently double counting.
                if (a.start().isBefore(ot.actualEnd()) && ot.actualStart().isBefore(a.end()))
                    throw new ResponseStatusException(HttpStatus.CONFLICT, "Actual OT overlaps another confirmed OT");
                otherNet += Duration.between(a.start(), a.end()).toMinutes() - a.breakMinutes();
            }
            dayContext.put(d, new OvertimeDailyCalculation.DayContext(regularNet, otherNet));
        }
        List<OvertimeDailyCalculation.DayResult> calculated;
        try {
            calculated = OvertimeDailyCalculation.calculate(
                    new OvertimeDailyCalculation.Interval(ot.actualStart(), ot.actualEnd()),
                    new OvertimeDailyCalculation.Interval(ot.approvedStart(), ot.approvedEnd()),
                    intervals, regularIntervals, dayContext::get);
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ex.getMessage());
        }
        List<PreviewDay> days = calculated.stream().map(x -> new PreviewDay(x.workDate(), x.actualNet(),
                x.withinApproval(), x.validMinutes(), x.overflowMinutes(), x.remainingDailyCapacity())).toList();
        boolean possible = days.stream().allMatch(x -> x.excessMinutes() == 0)
                && first.equals(last) && intervals.isEmpty();
        return new Preview(overtimeId, possible,
                possible ? "Preview only: use existing confirm endpoint subject to all service checks"
                        : "Read-only calculation; current schema/confirm service cannot persist and finalize these detailed allocations",
                days);
    }

    private record Request(long employee, LocalDateTime approvedStart, LocalDateTime approvedEnd,
            LocalDateTime actualStart, LocalDateTime actualEnd, String status, Long confirmedBy, int breakMinutes) {
    }

    private record Attendance(LocalDateTime start, LocalDateTime end, int breakMinutes) {
    }
}
