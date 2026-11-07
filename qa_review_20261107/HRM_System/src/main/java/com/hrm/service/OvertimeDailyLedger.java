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
    private record ActualInterval(LocalDateTime start, LocalDateTime end) {}
    private final JdbcTemplate db;

    public OvertimeDailyLedger(JdbcTemplate db) {
        this.db = db;
    }

    public Map<LocalDate, Long> netMinutes(long otId, LocalDateTime start, LocalDateTime end, int totalBreak) {
        if (start == null || end == null || !end.isAfter(start) || totalBreak < 0)
            throw invalid("Giờ OT thực tế không hợp lệ. Kết thúc phải sau bắt đầu, phút nghỉ không được âm.");
        var breaks = db.query(
                "SELECT break_start,break_end FROM dbo.overtime_breaks WHERE overtime_id=? AND is_active=1 ORDER BY break_start",
                (rs, n) -> new ActualInterval(rs.getObject(1, LocalDateTime.class),
                        rs.getObject(2, LocalDateTime.class)),
                otId);
        var actual = new OvertimeTimeMath.Span(start,end);
        var rests = breaks.stream().map(b -> new OvertimeTimeMath.Span(b.start(),b.end())).toList();
        Duration exact = OvertimeTimeMath.breaks(actual,rests);
        if (!rests.isEmpty() && !OvertimeTimeMath.aggregateMatches(rests,totalBreak,exact))
            throw invalid("OT break details disagree with aggregate minutes");
        if (rests.isEmpty() && totalBreak>0) {
            if (!start.toLocalDate().equals(end.minusNanos(1).toLocalDate()))
                throw invalid("Cross-day OT with breaks requires detailed intervals");
            Duration net = Duration.between(start,end).minusMinutes(totalBreak);
            if(net.isNegative()) throw invalid("OT breaks exceed actual duration");
            return Map.of(start.toLocalDate(),net.toMinutes());
        }
        var answer = OvertimeTimeMath.net(List.of(actual),rests);
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
