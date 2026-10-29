package com.hrm.service;

import java.time.*;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

/** Calculates reproducible, confirmed net OT minutes per calendar day. */
@Service
public class OvertimeDailyLedger {
    private final JdbcTemplate db;

    public OvertimeDailyLedger(JdbcTemplate db) {
        this.db = db;
    }

    public Map<LocalDate, Long> netMinutes(long otId, LocalDateTime start, LocalDateTime end, int totalBreak) {
        if (start == null || end == null || !end.isAfter(start) || start.getSecond() != 0 || end.getSecond() != 0
                || start.getNano() != 0 || end.getNano() != 0)
            throw invalid("OT times must be positive and minute-aligned");
        var breaks = db.query(
                "SELECT break_start,break_end FROM dbo.overtime_breaks WHERE overtime_id=? AND is_active=1 ORDER BY break_start",
                (rs, n) -> new OvertimeDailyCalculation.Interval(rs.getObject(1, LocalDateTime.class),
                        rs.getObject(2, LocalDateTime.class)),
                otId);
        long detailTotal = 0;
        LocalDateTime previous = null;
        for (var b : breaks) {
            if (b.start().isBefore(start) || b.end().isAfter(end) || (previous != null && b.start().isBefore(previous)))
                throw invalid("Invalid or overlapping OT break intervals");
            detailTotal += Duration.between(b.start(), b.end()).toMinutes();
            previous = b.end();
        }
        if (!breaks.isEmpty() && detailTotal != totalBreak)
            throw invalid("OT break details disagree with aggregate minutes");
        if (breaks.isEmpty() && totalBreak > 0 && !start.toLocalDate().equals(end.minusNanos(1).toLocalDate()))
            throw invalid("Cross-day OT with breaks requires detailed intervals");
        Map<LocalDate, Long> answer = new LinkedHashMap<>();
        for (LocalDate day = start.toLocalDate(); day.atStartOfDay().isBefore(end); day = day.plusDays(1)) {
            var lo = day.atStartOfDay();
            var hi = day.plusDays(1).atStartOfDay();
            var segmentStart = max(start, lo);
            var segmentEnd = min(end, hi);
            if (!segmentEnd.isAfter(segmentStart))
                continue;
            long minutes = Duration.between(segmentStart, segmentEnd).toMinutes();
            if (breaks.isEmpty())
                minutes -= totalBreak;
            else
                for (var b : breaks) {
                    var a = max(segmentStart, b.start());
                    var z = min(segmentEnd, b.end());
                    if (z.isAfter(a))
                        minutes -= Duration.between(a, z).toMinutes();
                }
            if (minutes < 0)
                throw invalid("OT breaks exceed actual minutes on calendar day");
            answer.put(day, minutes);
        }
        return answer;
    }

    private static LocalDateTime max(LocalDateTime a, LocalDateTime b) {
        return a.isAfter(b) ? a : b;
    }

    private static LocalDateTime min(LocalDateTime a, LocalDateTime b) {
        return a.isBefore(b) ? a : b;
    }

    private static ResponseStatusException invalid(String msg) {
        return new ResponseStatusException(HttpStatus.CONFLICT, msg);
    }
}
