package com.hrm.util;

import java.time.*;
import java.util.*;

/** Pure computations, independently testable. Timestamps are local SQL Server datetime2 values. */
public final class TimeIntervalRules {
 private TimeIntervalRules() {}
 public record Span(LocalDateTime start,LocalDateTime end){
  public Span{if(start==null||end==null||!start.isBefore(end))throw new IllegalArgumentException("Invalid time range");}
 }
 public static boolean overlaps(Span a,Span b){return a.start().isBefore(b.end())&&b.start().isBefore(a.end());}
 public static Optional<Span> intersect(Span a,Span b){
  LocalDateTime s=a.start().isAfter(b.start())?a.start():b.start();
  LocalDateTime e=a.end().isBefore(b.end())?a.end():b.end();
  return s.isBefore(e)?Optional.of(new Span(s,e)):Optional.empty();
 }
 public static long minutes(Span span){return Duration.between(span.start(),span.end()).toMinutes();}
 public static List<Span> portionsOutside(Span actual,Span approved){
  List<Span> result=new ArrayList<>();
  if(actual.start().isBefore(approved.start())){
   LocalDateTime e=actual.end().isBefore(approved.start())?actual.end():approved.start();
   if(actual.start().isBefore(e)) result.add(new Span(actual.start(),e));
  }
  if(actual.end().isAfter(approved.end())){
   LocalDateTime s=actual.start().isAfter(approved.end())?actual.start():approved.end();
   if(s.isBefore(actual.end()))result.add(new Span(s,actual.end()));
  }
  return List.copyOf(result);
 }
 /** Valid minutes within approved interval, minus actual break intervals and ordinary scheduled work. */
 public static long recognizedMinutes(Span actual,Span approved,List<Span> breaks,List<Span> normalSchedule){
  Optional<Span> eligible=intersect(actual,approved);
  if(eligible.isEmpty())return 0;
  Span base=eligible.get();
  List<Span> blocked=new ArrayList<>();
  for(Span b:breaks)intersect(base,b).ifPresent(blocked::add);
  for(Span w:normalSchedule)intersect(base,w).ifPresent(blocked::add);
  blocked.sort(Comparator.comparing(Span::start));
  LocalDateTime cursor=base.start();long recognized=0;
  for(Span block:blocked){
   if(block.start().isAfter(cursor))recognized+=Duration.between(cursor,block.start()).toMinutes();
   if(block.end().isAfter(cursor))cursor=block.end();
  }
  if(base.end().isAfter(cursor))recognized+=Duration.between(cursor,base.end()).toMinutes();
  return recognized;
 }
}
