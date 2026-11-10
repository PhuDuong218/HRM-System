package com.hrm;
import com.hrm.service.*;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.web.server.ResponseStatusException;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class OvertimeP1TimeMathTest {
 private final LocalDateTime start=LocalDateTime.parse("2026-11-02T18:00:00");
 private OvertimeTimeMath.Span span(LocalDateTime a,LocalDateTime b){return new OvertimeTimeMath.Span(a,b);}
 @Test void twoShortRestsDoNotDisappear(){var rests=List.of(span(start.plusMinutes(1),start.plusMinutes(1).plusSeconds(59)),span(start.plusMinutes(3),start.plusMinutes(3).plusSeconds(59)));assertEquals(28L,OvertimeTimeMath.net(List.of(span(start,start.plusMinutes(30))),rests).get(start.toLocalDate()));}
 @Test void fractionalSecondsAreNotRoundedBeforeSubtraction(){assertEquals(0L,OvertimeTimeMath.net(List.of(span(start,start.plusSeconds(60).plusNanos(100_000_000))),List.of(span(start.plusSeconds(1),start.plusSeconds(1).plusNanos(200_000_000)))).get(start.toLocalDate()));}
 @Test void multipleApprovedWindowsAreSummedBeforeDailyFloor(){assertEquals(1L,OvertimeTimeMath.net(List.of(span(start,start.plusSeconds(40)),span(start.plusMinutes(2),start.plusMinutes(2).plusSeconds(40))),List.of()).get(start.toLocalDate()));}
 @Test void midnightDoesNotTransferFractionalMinutes(){var a=LocalDateTime.parse("2026-11-02T23:59:30");var result=OvertimeTimeMath.net(List.of(span(a,a.plusSeconds(90))),List.of());assertEquals(0L,result.get(a.toLocalDate()));assertEquals(1L,result.get(a.toLocalDate().plusDays(1)));}
 @Test void exactMidnightDoesNotCreateAnEmptyNextDay(){var a=LocalDateTime.parse("2026-11-02T23:59:00");assertEquals(Map.of(a.toLocalDate(),1L),OvertimeTimeMath.net(List.of(span(a,a.plusMinutes(1))),List.of()));}
 @Test void overlappingRestsAreRejected(){var actual=span(start,start.plusHours(1));assertThrows(ResponseStatusException.class,()->OvertimeTimeMath.breaks(actual,List.of(span(start,start.plusMinutes(10)),span(start.plusMinutes(9),start.plusMinutes(11)))));}
 @Test void readersUseDetailedDurationsEvenForLegacyRoundedAggregate(){
  var db=mock(JdbcTemplate.class);
  // Feed real row mappers rather than depending on private record types.
  doAnswer(call->{RowMapper<?> mapper=call.getArgument(1);List<Object> rows=new ArrayList<>();for(int minute:new int[]{1,3}){var rs=mock(java.sql.ResultSet.class);when(rs.getObject(1,LocalDateTime.class)).thenReturn(start.plusMinutes(minute));when(rs.getObject(2,LocalDateTime.class)).thenReturn(start.plusMinutes(minute).plusSeconds(59));rows.add(mapper.mapRow(rs,rows.size()));}return rows;}).when(db).query(contains("FROM dbo.overtime_breaks"),any(RowMapper.class),eq(7L));
  doReturn(List.of()).when(db).query(contains("FROM dbo.overtime_overflow_intervals"),any(RowMapper.class),eq(7L));
  for(int aggregate:new int[]{0,1}){assertEquals(28L,new OvertimeDailyLedger(db).netMinutes(7L,start,start.plusMinutes(30),aggregate).get(start.toLocalDate()));assertEquals(28L,new OvertimeRecognizedMinutes(db).recognize(7L,start,start.plusMinutes(30),start,start.plusMinutes(30),aggregate).get(start.toLocalDate()));}
 }
}
