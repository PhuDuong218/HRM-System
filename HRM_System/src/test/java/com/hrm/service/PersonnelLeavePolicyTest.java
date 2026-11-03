package com.hrm.service;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.jdbc.core.*;
import org.springframework.web.server.ResponseStatusException;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class PersonnelLeavePolicyTest {
 JdbcTemplate db;PersonnelWorkflowSupport support;LeaveRequestRules rules;PayrollHistoryGuard payroll;
 LocalDate day=LocalDate.of(2026,11,10);
 @BeforeEach void setup(){db=mock(JdbcTemplate.class);support=mock(PersonnelWorkflowSupport.class);payroll=mock(PayrollHistoryGuard.class);rules=new LeaveRequestRules(db,payroll,support);when(support.today()).thenReturn(day);when(support.localNow()).thenReturn(day.atTime(9,0));}
 @Test void annualGrantProratesLeapYearAndUsesConfiguredHours(){
  var policy=new LeaveAccrualService.Policy(14,new BigDecimal("7.5"),false);
  assertEquals(new BigDecimal("105.0000"),LeaveAccrualService.calculate(policy,LocalDate.of(2020,1,1),LocalDate.of(2024,1,1),LocalDate.of(2025,1,1)));
  assertEquals(new BigDecimal("52.7869"),LeaveAccrualService.calculate(policy,LocalDate.of(2024,7,1),LocalDate.of(2024,1,1),LocalDate.of(2025,1,1)));
 }
 @Test void seniorityIsExplicitAndNoGrantBeforeHire(){
  var policy=new LeaveAccrualService.Policy(12,new BigDecimal("8"),true);
  assertEquals(new BigDecimal("112.0000"),LeaveAccrualService.calculate(policy,LocalDate.of(2015,1,1),LocalDate.of(2026,1,1),LocalDate.of(2027,1,1)));
  assertEquals(new BigDecimal("0.0000"),LeaveAccrualService.calculate(policy,LocalDate.of(2027,1,1),LocalDate.of(2026,1,1),LocalDate.of(2027,1,1)));
 }
 @SuppressWarnings("unchecked") private void breaks(LocalDateTime a,LocalDateTime b){
  when(db.queryForObject(contains("OBJECT_ID"),eq(Integer.class))).thenReturn(1);
  when(db.queryForObject(contains("break_detail_status"),eq(String.class),eq(5L))).thenReturn("VERIFIED");
  when(db.query(contains("break_start,break_end"),any(RowMapper.class),eq(5L))).thenReturn(List.<LocalDateTime[]>of(new LocalDateTime[]{a,b}));
 }
 @Test void partialLeaveSubtractsOnlyIntersectingBreakNotHalfOfDailyTotal(){
  var start=day.atTime(8,0);var end=day.atTime(17,0);breaks(day.atTime(12,0),day.atTime(13,0));
  assertEquals(4*3600000L,rules.netMillis(5,start,end,start,day.atTime(12,0),60));
  assertEquals(4*3600000L,rules.netMillis(5,start,end,day.atTime(12,0),end,60));
  assertEquals(30*60000L,rules.netMillis(5,start,end,day.atTime(11,30),day.atTime(12,30),60));
 }
 @Test void overnightBreakUsesFullDatesAndMismatchedBreakIsBlocked(){
  var start=day.atTime(22,0);var end=day.plusDays(1).atTime(6,0);breaks(day.plusDays(1).atTime(1,0),day.plusDays(1).atTime(2,0));
  assertEquals(3*3600000L,rules.netMillis(5,start,end,start,day.plusDays(1).atTime(2,0),60));
  assertEquals(409,assertThrows(ResponseStatusException.class,()->rules.netMillis(5,start,end,start,day.plusDays(1).atTime(2,0),30)).getStatusCode().value());
 }
 @SuppressWarnings("unchecked") @Test void sevenCalendarDayBoundaryAndOriginalSubmissionArePreserved(){
  when(db.queryForObject(contains("OBJECT_ID"),eq(Integer.class))).thenReturn(1);
  when(db.queryForObject(contains("COL_LENGTH"),eq(Integer.class))).thenReturn(0);
  when(db.query(contains("retroactive_days"),any(RowMapper.class),eq(7L))).thenReturn(List.of(new LeaveRequestRules.Rules(7,0,null)));
  when(db.queryForObject(contains("COUNT_BIG"),eq(Long.class),any(),any(),any())).thenReturn(0L);
  assertDoesNotThrow(()->rules.validateTime(1,7,day.minusDays(7).atTime(8,0),day.minusDays(7).atTime(17,0),null));
  assertEquals(400,assertThrows(ResponseStatusException.class,()->rules.validateTime(1,7,day.minusDays(8).atTime(8,0),day.minusDays(8).atTime(17,0),null)).getStatusCode().value());
  when(db.query(contains("retroactive_days"),any(RowMapper.class),eq(7L))).thenReturn(List.of(new LeaveRequestRules.Rules(0,0,null)));
  assertDoesNotThrow(()->rules.validateTime(1,7,day.minusDays(1).atTime(8,0),day.minusDays(1).atTime(17,0),day.minusDays(2).atTime(9,0)));
 }
}
