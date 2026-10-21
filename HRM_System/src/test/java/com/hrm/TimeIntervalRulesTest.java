package com.hrm.util;
import org.junit.jupiter.api.Test;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static com.hrm.util.TimeIntervalRules.*;
class TimeIntervalRulesTest {
 private static Span s(String start,String end){return new Span(LocalDateTime.parse(start),LocalDateTime.parse(end));}
 @Test void overlapTouchesBoundaryIsAllowed(){
  assertFalse(overlaps(s("2026-10-20T08:00","2026-10-20T12:00"),s("2026-10-20T12:00","2026-10-20T17:00")));
  assertTrue(overlaps(s("2026-10-20T22:00","2026-10-21T06:00"),s("2026-10-21T05:00","2026-10-21T07:00")));
 }
 @Test void otOverrunNotAutoRecognized(){
  var actual=s("2026-10-20T16:50","2026-10-20T20:30");
  var approved=s("2026-10-20T17:00","2026-10-20T20:00");
  assertEquals(2,portionsOutside(actual,approved).size());
  assertEquals(180,recognizedMinutes(actual,approved,List.of(),List.of()));
 }
 @Test void breaksAndNormalAreNotDoubleSubtracted(){
  assertEquals(90,recognizedMinutes(s("2026-10-20T17:00","2026-10-20T20:00"),
   s("2026-10-20T17:00","2026-10-20T20:00"),
   List.of(s("2026-10-20T18:00","2026-10-20T18:30")),
   List.of(s("2026-10-20T17:00","2026-10-20T18:00"))));
 }
}
