package com.hrm.employee;
import java.time.*;
import org.junit.Test;
import static org.junit.Assert.*;
public class CalendarMonthTest {
 @Test public void novemberUsesCompleteMondayWeeks(){YearMonth m=YearMonth.of(2026,11);assertEquals(LocalDate.of(2026,10,26),CalendarMonth.firstCell(m));assertEquals(LocalDate.of(2026,12,6),CalendarMonth.lastCell(m));}
 @Test public void leapFebruaryIncludesLeapDay(){YearMonth m=YearMonth.of(2028,2);assertFalse(CalendarMonth.lastCell(m).isBefore(m.atDay(29)));assertEquals(DayOfWeek.MONDAY,CalendarMonth.firstCell(m).getDayOfWeek());}
 @Test public void overnightAppearsOnBothDaysButNotAfterMidnightEnd(){assertTrue(CalendarMonth.overlaps(LocalDate.of(2026,11,4),"2026-11-04T22:00:00","2026-11-05T06:00:00"));assertTrue(CalendarMonth.overlaps(LocalDate.of(2026,11,5),"2026-11-04T22:00:00","2026-11-05T06:00:00"));assertFalse(CalendarMonth.overlaps(LocalDate.of(2026,11,5),"2026-11-04T22:00:00","2026-11-05T00:00:00"));}
}
