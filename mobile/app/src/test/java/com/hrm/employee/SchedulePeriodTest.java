package com.hrm.employee;
import org.junit.Test;
import java.time.*;
import static org.junit.Assert.*;
public class SchedulePeriodTest {
 @Test public void isoWeekMatchesReference(){SchedulePeriod p=new SchedulePeriod(LocalDate.of(2026,11,2));assertEquals(45,p.week());assertEquals(LocalDate.of(2026,11,2),p.first());assertEquals(LocalDate.of(2026,11,8),p.last());assertTrue(p.contains(LocalDate.of(2026,11,8)));assertFalse(p.contains(LocalDate.of(2026,11,9)));}
 @Test public void sundayUsesPreviousMonday(){SchedulePeriod p=new SchedulePeriod(LocalDate.of(2026,11,8));assertEquals(LocalDate.of(2026,11,2),p.first());p.move(1);assertEquals(LocalDate.of(2026,11,9),p.first());p.move(-1);assertEquals(LocalDate.of(2026,11,2),p.first());}
 @Test public void weekBoundaryAcrossYear(){SchedulePeriod p=new SchedulePeriod(LocalDate.of(2027,1,1));assertEquals(LocalDate.of(2026,12,28),p.first());assertEquals(LocalDate.of(2027,1,3),p.last());assertEquals(53,p.week());p.move(1);assertEquals(1,p.week());}
 @Test public void dailyNavigationIncludesLeapDay(){SchedulePeriod p=new SchedulePeriod(LocalDate.of(2028,2,28));p.weekly(false);p.move(1);assertEquals(LocalDate.of(2028,2,29),p.first());assertEquals(p.first(),p.last());p.move(1);assertEquals(LocalDate.of(2028,3,1),p.first());}
 @Test public void switchingModesKeepsSelectedDay(){SchedulePeriod p=new SchedulePeriod(LocalDate.of(2026,11,5));p.weekly(false);assertEquals(LocalDate.of(2026,11,5),p.first());p.weekly(true);assertEquals(LocalDate.of(2026,11,2),p.first());assertEquals(LocalDate.of(2026,11,5),p.anchor());}
 @Test public void overnightAndMidnightBoundaries(){LocalDate d=LocalDate.of(2026,11,2);assertTrue(SchedulePeriod.overlaps(d,d.minusDays(1).atTime(22,0),d.atTime(6,0)));assertFalse(SchedulePeriod.overlaps(d,d.minusDays(1).atTime(22,0),d.atStartOfDay()));assertFalse(SchedulePeriod.overlaps(d,d.plusDays(1).atStartOfDay(),d.plusDays(1).atTime(3,0)));assertFalse(SchedulePeriod.overlaps(d,d.atTime(8,0),d.atTime(7,0)));}
}
