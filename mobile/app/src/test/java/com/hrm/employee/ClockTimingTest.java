package com.hrm.employee;
import org.junit.Test;
import static org.junit.Assert.*;
public class ClockTimingTest {
 @Test public void lateCheckInKeepsSeconds(){assertEquals("Check-in muộn 1 giờ 1 phút 52 giây",ClockTiming.describe("2026-11-04T08:00:00","2026-11-04T09:01:52.152",true));}
 @Test public void checkoutEarlyAndLate(){assertEquals("Check-out sớm 15 phút",ClockTiming.describe("2026-11-04T17:00:00","2026-11-04T16:45:00",false));assertEquals("Check-out muộn 20 phút",ClockTiming.describe("2026-11-04T17:00:00","2026-11-04T17:20:00",false));}
 @Test public void overnightUsesDates(){assertEquals("Check-out sớm 10 phút",ClockTiming.describe("2026-11-05T06:00:00","2026-11-05T05:50:00",false));assertEquals("Check-in sớm 15 phút",ClockTiming.describe("2026-11-05T00:00:00","2026-11-04T23:45:00",true));}
 @Test public void missingTimeIsNotAssumed(){assertEquals("",ClockTiming.describe("2026-11-04T08:00:00","",true));assertEquals("",ClockTiming.describe("bad","bad",true));}
 @Test public void onTimeAndSubMinute(){assertEquals("Check-in đúng giờ",ClockTiming.describe("2026-11-04T08:00:00","2026-11-04T08:00:00",true));assertEquals("Check-in muộn 30 giây",ClockTiming.describe("2026-11-04T08:00:00","2026-11-04T08:00:30",true));}
 @Test public void hourBoundaryAndMultipleHours(){assertEquals("Check-in muộn 59 phút 59 giây",ClockTiming.describe("2026-11-04T08:00:00","2026-11-04T08:59:59",true));assertEquals("Check-in muộn 1 giờ 0 phút 0 giây",ClockTiming.describe("2026-11-04T08:00:00","2026-11-04T09:00:00",true));assertEquals("Check-out sớm 2 giờ 12 phút 11 giây",ClockTiming.describe("2026-11-04T17:00:00","2026-11-04T14:47:49",false));}
}
