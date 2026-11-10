package com.hrm.employee;
import java.time.Duration;
import java.time.LocalDateTime;
/** Display only; this does not change the server's grace period or worked minutes. */
final class ClockTiming {
 static String describe(String planned,String actual,boolean checkIn){
  if(planned==null||actual==null||planned.isEmpty()||actual.isEmpty())return "";
  try{
   long millis=Duration.between(LocalDateTime.parse(planned),LocalDateTime.parse(actual)).toMillis();
   String label=checkIn?"Check-in":"Check-out";
   if(millis==0)return label+" đúng giờ";
   long seconds=Math.abs(millis)/1000,minutes=seconds/60,rest=seconds%60;
   String amount=seconds>=3600?(seconds/3600)+" giờ "+(minutes%60)+" phút "+rest+" giây":minutes>0?minutes+" phút"+(rest>0?" "+rest+" giây":""):seconds>0?seconds+" giây":"dưới 1 giây";
   return label+(millis>0?" muộn ":" sớm ")+amount;
  }catch(java.time.DateTimeException e){return "";}
 }
}
