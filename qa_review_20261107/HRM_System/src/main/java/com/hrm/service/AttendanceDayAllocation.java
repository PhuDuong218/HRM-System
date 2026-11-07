package com.hrm.service;
import java.time.*;
import java.util.*;

/** Does not infer actual breaks from a schedule. Null detail means not verified. */
public final class AttendanceDayAllocation {
 private AttendanceDayAllocation() {}
 public static Map<LocalDate,Long> allocate(LocalDateTime start,LocalDateTime end,Integer aggregate,
         List<OvertimeTimeMath.Span> details) {
  OvertimeTimeMath.require(start!=null&&end!=null&&end.isAfter(start)&&aggregate!=null&&aggregate>=0,"Incomplete actual attendance");
  var actual=new OvertimeTimeMath.Span(start,end);
  if(details!=null){
   var total=OvertimeTimeMath.breaks(actual,details);
   OvertimeTimeMath.require(total.toMinutes()==aggregate,"Actual break details disagree with attendance");
   return OvertimeTimeMath.net(List.of(actual),details);
  }
  if(aggregate==0)return OvertimeTimeMath.net(List.of(actual),List.of());
  OvertimeTimeMath.require(start.toLocalDate().equals(end.minusNanos(1).toLocalDate()),"Cross-day attendance requires verified actual break details");
  var net=Duration.between(start,end).minusMinutes(aggregate);
  OvertimeTimeMath.require(!net.isNegative(),"Breaks exceed actual attendance");
  return Map.of(start.toLocalDate(),net.toMinutes());
 }
}
