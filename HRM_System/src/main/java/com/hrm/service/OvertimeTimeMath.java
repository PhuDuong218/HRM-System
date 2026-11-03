package com.hrm.service;

import java.time.*;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/** Business-local intervals; floor once per calendar day AFTER exact subtraction. */
public final class OvertimeTimeMath {
    private OvertimeTimeMath() {}
    public record Span(LocalDateTime start, LocalDateTime end) {}
    public static void require(boolean condition, String reason) {
        if (!condition) throw new ResponseStatusException(HttpStatus.CONFLICT, reason);
    }
    public static Duration duration(Span span) {
        require(span != null && span.start() != null && span.end() != null
                && span.end().isAfter(span.start()), "Invalid OT interval");
        return Duration.between(span.start(), span.end());
    }
    public static Duration breaks(Span actual, List<Span> rests) {
        duration(actual);
        List<Span> sorted = new ArrayList<>(rests);
        sorted.forEach(OvertimeTimeMath::duration);
        sorted.sort(Comparator.comparing(Span::start));
        LocalDateTime last = actual.start();
        Duration total = Duration.ZERO;
        for (Span rest : sorted) {
            require(!rest.start().isBefore(last) && !rest.end().isAfter(actual.end()),
                    "Invalid or overlapping detailed OT breaks");
            total = total.plus(duration(rest)); last = rest.end();
        }
        return total;
    }
    public static boolean aggregateMatches(List<Span> rests, int aggregate, Duration exact) {
        // Older rows stored SUM(floor(each break)); tolerate that metadata only.
        // Net payable minutes ALWAYS come from exact persisted intervals.
        long legacy = rests.stream().mapToLong(s -> duration(s).toMinutes()).sum();
        return aggregate >= 0 && (aggregate == exact.toMinutes() || aggregate == legacy);
    }
    public static Map<LocalDate,Long> net(List<Span> windows, List<Span> rests) {
        Map<LocalDate,Duration> totals = new TreeMap<>();
        LocalDateTime previous = null;
        List<Span> sorted = new ArrayList<>(windows);
        sorted.forEach(OvertimeTimeMath::duration);
        sorted.sort(Comparator.comparing(Span::start));
        for (Span window : sorted) {
            require(previous == null || !window.start().isBefore(previous), "Approved OT windows overlap");
            previous = window.end();
            for (LocalDate day=window.start().toLocalDate(); day.atStartOfDay().isBefore(window.end()); day=day.plusDays(1)) {
                LocalDateTime start=max(window.start(),day.atStartOfDay()),end=min(window.end(),day.plusDays(1).atStartOfDay());
                Duration value=Duration.between(start,end);
                for (Span rest : rests) {
                    LocalDateTime a=max(start,rest.start()),b=min(end,rest.end());
                    if (a.isBefore(b)) value=value.minus(Duration.between(a,b));
                }
                require(!value.isNegative(),"OT breaks exceed working interval");
                totals.merge(day,value,Duration::plus);
            }
        }
        Map<LocalDate,Long> result=new TreeMap<>();
        totals.forEach((day,value)->result.put(day,value.toMinutes()));
        return result;
    }
    public static LocalDateTime max(LocalDateTime a,LocalDateTime b){return a.isAfter(b)?a:b;}
    public static LocalDateTime min(LocalDateTime a,LocalDateTime b){return a.isBefore(b)?a:b;}
}
