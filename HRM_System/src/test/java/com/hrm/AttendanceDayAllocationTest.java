package com.hrm;
import com.hrm.service.*;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;
import static org.junit.jupiter.api.Assertions.*;
class AttendanceDayAllocationTest {
 private final LocalDateTime start=LocalDateTime.parse("2026-10-31T22:00:00"),end=LocalDateTime.parse("2026-11-01T06:00:00");
 @Test void overnightWithoutBreaksIsAllocatedAcrossMonths(){assertEquals(Map.of(start.toLocalDate(),120L,end.toLocalDate(),360L),AttendanceDayAllocation.allocate(start,end,0,null));}
 @Test void verifiedBreakCrossingMidnightIsSplitExactly(){var result=AttendanceDayAllocation.allocate(start,end,30,List.of(new OvertimeTimeMath.Span(start.plusHours(1).plusMinutes(50),end.minusHours(5).minusMinutes(40))));assertEquals(110L,result.get(start.toLocalDate()));assertEquals(340L,result.get(end.toLocalDate()));}
 @Test void positiveAggregateAloneCannotInventCrossDayBreaks(){assertThrows(ResponseStatusException.class,()->AttendanceDayAllocation.allocate(start,end,30,null));}
 @Test void midnightDoesNotCreateAnotherDay(){assertEquals(Map.of(start.toLocalDate(),120L),AttendanceDayAllocation.allocate(start,start.plusHours(2),0,null));}
 @Test void newYearIsHandledLikeOtherMidnights(){var a=LocalDateTime.parse("2026-12-31T23:00:00");assertEquals(Map.of(a.toLocalDate(),60L,a.toLocalDate().plusDays(1),120L),AttendanceDayAllocation.allocate(a,a.plusHours(3),0,null));}
 @Test void sameDayLegacyAggregateStillWorks(){assertEquals(Map.of(start.toLocalDate(),90L),AttendanceDayAllocation.allocate(start,start.plusHours(2),30,null));}
 @Test void mismatchedActualBreaksAreRejected(){assertThrows(ResponseStatusException.class,()->AttendanceDayAllocation.allocate(start,end,60,List.of(new OvertimeTimeMath.Span(start.plusMinutes(10),start.plusMinutes(20)))));}
}
