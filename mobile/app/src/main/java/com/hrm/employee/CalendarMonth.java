package com.hrm.employee;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.TemporalAdjusters;

/** Date-only month cells; Monday first and stable across device time zones. */
final class CalendarMonth {
    static LocalDate firstCell(YearMonth month) {
        return month.atDay(1).with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
    }
    static LocalDate lastCell(YearMonth month) {
        return month.atEndOfMonth().with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY));
    }
    static boolean overlaps(LocalDate date, String start, String end) {
        return start != null && end != null && start.compareTo(date.plusDays(1) + "T00:00:00") < 0
                && end.compareTo(date + "T00:00:00") > 0;
    }
}
