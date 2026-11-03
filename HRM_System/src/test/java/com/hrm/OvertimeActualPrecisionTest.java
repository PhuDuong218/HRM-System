package com.hrm;
import com.hrm.service.OvertimePlanningService;
import com.hrm.dto.OvertimeDTOs.BreakInput;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;
import static org.junit.jupiter.api.Assertions.*;
class OvertimeActualPrecisionTest {
 private final LocalDateTime start=LocalDateTime.parse("2026-11-02T16:51:06.813"),end=LocalDateTime.parse("2026-11-02T18:44:37.095");
 @Test void actualClockPrecisionIsAcceptedAndPreserved(){assertTrue(OvertimePlanningService.validateActual(start,end,List.of()).isEmpty());assertEquals(113L,OvertimePlanningService.net(start,end,List.of()).values().stream().mapToLong(Long::longValue).sum());}
 @Test void plannedTimesStillRequireWholeMinutes(){assertThrows(ResponseStatusException.class,()->OvertimePlanningService.validate(start,end,List.of()));}
 @Test void actualRestPrecisionIsPreserved(){var rest=new BreakInput(start.plusMinutes(10).plusNanos(123000000),start.plusMinutes(20).plusSeconds(5));assertEquals(List.of(rest),OvertimePlanningService.validateActual(start,end,List.of(rest)));}
 @Test void actualRestOutsideShiftIsRejected(){assertThrows(ResponseStatusException.class,()->OvertimePlanningService.validateActual(start,end,List.of(new BreakInput(start.minusSeconds(1),start.plusMinutes(5)))));}
 @Test void overlappingActualRestsAreRejected(){assertThrows(ResponseStatusException.class,()->OvertimePlanningService.validateActual(start,end,List.of(new BreakInput(start.plusMinutes(1),start.plusMinutes(10)),new BreakInput(start.plusMinutes(9),start.plusMinutes(12)))));}
 @Test void reversedActualTimeIsRejected(){assertThrows(ResponseStatusException.class,()->OvertimePlanningService.validateActual(end,start,List.of()));}
 @Test void dailyLedgerAcceptsSavedServerPrecision(){
  var db=org.mockito.Mockito.mock(org.springframework.jdbc.core.JdbcTemplate.class);
  org.mockito.Mockito.doReturn(List.of()).when(db).query(org.mockito.ArgumentMatchers.anyString(),org.mockito.ArgumentMatchers.any(org.springframework.jdbc.core.RowMapper.class),org.mockito.ArgumentMatchers.eq(7L));
  var result=new com.hrm.service.OvertimeDailyLedger(db).netMinutes(7L,start,end,0);
  assertEquals(113L,result.get(start.toLocalDate()));
 }
}