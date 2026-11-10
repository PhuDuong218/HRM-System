package com.hrm.service;

import java.time.*;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

/**
 * Valid-payroll OT net minutes = originally approved actual intersection +
 * approved overflow, minus breaks.
 */
@Service
public class OvertimeRecognizedMinutes {
    private record Interval(LocalDateTime start, LocalDateTime end) {
    }

    private final JdbcTemplate db;

    public OvertimeRecognizedMinutes(JdbcTemplate db) {
        this.db = db;
    }

    private static LocalDateTime max(LocalDateTime a, LocalDateTime b) {
        return a.isAfter(b) ? a : b;
    }

    private static LocalDateTime min(LocalDateTime a, LocalDateTime b) {
        return a.isBefore(b) ? a : b;
    }

    private static void assertValid(boolean condition, String message) {
        if (!condition)
            throw new ResponseStatusException(HttpStatus.CONFLICT, message);
    }

    public Map<LocalDate, Long> recognize(long otId, LocalDateTime actualStart, LocalDateTime actualEnd,
            LocalDateTime requestStart, LocalDateTime requestEnd, int breakMinutes) {
        assertValid(actualStart != null && actualEnd != null && actualStart.isBefore(actualEnd), "Actual OT missing");
        List<Interval> rests = db.query(
                "SELECT break_start,break_end FROM dbo.overtime_breaks WHERE overtime_id=? AND is_active=1 ORDER BY break_start",
                (r, i) -> new Interval(r.getObject(1, LocalDateTime.class), r.getObject(2, LocalDateTime.class)), otId);

        var actual=new OvertimeTimeMath.Span(actualStart,actualEnd);
        var exactRests=rests.stream().map(r->new OvertimeTimeMath.Span(r.start(),r.end())).toList();
        var exact=OvertimeTimeMath.breaks(actual,exactRests);
        assertValid(OvertimeTimeMath.aggregateMatches(exactRests,breakMinutes,exact),"OT detailed break totals mismatch");
        List<Interval> windows = new ArrayList<>();
        LocalDateTime lo = max(actualStart, requestStart), hi = min(actualEnd, requestEnd);
        if (lo.isBefore(hi))
            windows.add(new Interval(lo, hi));
        windows.addAll(db.query("""
                SELECT i.approved_start,i.approved_end FROM dbo.overtime_overflow_intervals i
                JOIN dbo.overtime_request_details d ON d.request_id=i.request_id
                JOIN dbo.employee_requests r ON r.request_id=d.request_id
                WHERE d.overtime_id=? AND r.request_type='OT_VUOT' AND r.status='DA_DUYET'
                AND i.approved_start IS NOT NULL ORDER BY i.approved_start
                """, (r, i) -> new Interval(r.getObject(1, LocalDateTime.class), r.getObject(2, LocalDateTime.class)),
                otId));
        windows.sort(Comparator.comparing(Interval::start));
        LocalDateTime last = null;

        for (Interval w : windows) {
            assertValid(w.start() != null && w.end() != null && w.start().isBefore(w.end())
                    && !w.start().isBefore(actualStart) && !w.end().isAfter(actualEnd), "Invalid approved window");
            assertValid(last == null || !w.start().isBefore(last), "Approved OT windows overlap");
            last = w.end();
        }
        return OvertimeTimeMath.net(windows.stream().map(w->new OvertimeTimeMath.Span(w.start(),w.end())).toList(),exactRests);

    }
}
