package com.hrm.employee;
import java.time.*;
import java.time.temporal.*;
/** Calendar navigation uses ISO weeks (Monday through Sunday). */
public final class SchedulePeriod {
 private LocalDate anchor;private boolean weekly=true;
 public SchedulePeriod(LocalDate date){anchor=date;}
 public LocalDate anchor(){return anchor;}
 public void select(LocalDate date){anchor=date;}
 public boolean weekly(){return weekly;}
 public void weekly(boolean value){weekly=value;}
 public LocalDate first(){return weekly?anchor.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)):anchor;}
 public LocalDate last(){return weekly?first().plusDays(6):anchor;}
 public void move(int direction){anchor=weekly?anchor.plusWeeks(direction):anchor.plusDays(direction);}
 public int week(){return first().get(WeekFields.ISO.weekOfWeekBasedYear());}
 public boolean contains(LocalDate date){return !date.isBefore(first())&&!date.isAfter(last());}
 public static boolean overlaps(LocalDate day,LocalDateTime start,LocalDateTime end){return start!=null&&end!=null&&end.isAfter(start)&&start.isBefore(day.plusDays(1).atStartOfDay())&&end.isAfter(day.atStartOfDay());}
}
