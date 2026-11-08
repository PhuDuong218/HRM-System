package com.hrm.service;

import java.time.LocalDate;
import java.util.UUID;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.server.ResponseStatusException;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class EmploymentExitReadinessTest {
 JdbcTemplate db;PayrollHistoryGuard payroll;EmploymentExitGuard guard;
 final LocalDate last=LocalDate.of(2030,1,20);
 @BeforeEach void setup(){
  var source=new JdbcDataSource();source.setURL("jdbc:h2:mem:"+UUID.randomUUID()+";MODE=MSSQLServer;DB_CLOSE_DELAY=-1");
  db=new JdbcTemplate(source){@Override public <T>T queryForObject(String sql,Class<T> type,Object...args){return super.queryForObject(sql.replace("COUNT_BIG(*)","COUNT(*)"),type,args);}};
  db.execute("CREATE SCHEMA dbo");
  db.execute("CREATE TABLE dbo.positions(position_id bigint,position_code varchar(40),position_name varchar(100))");
  db.execute("CREATE TABLE dbo.employee_assignments(assignment_id bigint,employee_id bigint,position_id bigint,effective_from date,effective_to date)");
  db.execute("CREATE TABLE dbo.work_schedules(schedule_id bigint,employee_id bigint,planned_start timestamp,planned_end timestamp,cancelled_at timestamp)");
  db.execute("CREATE TABLE dbo.contracts(contract_id bigint,employee_id bigint,contract_number varchar(40),start_date date,end_date date,status varchar(20))");
  db.execute("CREATE TABLE dbo.employee_requests(request_id bigint,employee_id bigint,request_code varchar(40),status varchar(20))");
  db.execute("CREATE TABLE dbo.leave_request_details(request_id bigint,leave_start timestamp,leave_end timestamp)");
  db.execute("CREATE TABLE dbo.employee_request_withdrawals(request_id bigint)");
  db.execute("CREATE TABLE dbo.attendance_records(attendance_id bigint,employee_id bigint,work_date date,verification_status varchar(30),invalidated_at timestamp)");
  payroll=mock(PayrollHistoryGuard.class);guard=new EmploymentExitGuard(db,payroll);
 }
 @Test void noBlockersAndMidnightBoundaryAreReady(){
  db.update("INSERT INTO dbo.work_schedules VALUES(1,12,'2030-01-20 22:00:00','2030-01-21 00:00:00',NULL)");
  db.update("INSERT INTO dbo.contracts VALUES(1,12,'HD-1','2029-01-01','2030-01-20','DA_KY')");
  assertTrue(guard.inspect(12,last).isEmpty());assertDoesNotThrow(()->guard.check(12,last));
 }
 @Test void reportsEveryBlockerWithExactCountsAndLimitedExamples(){
  when(payroll.lockedPeriodCount(12L,last.plusDays(1),null)).thenReturn(2L);
  db.update("INSERT INTO dbo.positions VALUES(1,'TRUONG_PHONG','Trưởng phòng')");
  db.update("INSERT INTO dbo.employee_assignments VALUES(1,12,1,'2029-01-01',NULL),(2,12,2,'2030-01-22',NULL)");
  for(int i=1;i<=8;i++)db.update("INSERT INTO dbo.work_schedules VALUES(?,12,'2030-01-21 08:00:00','2030-01-21 17:00:00',NULL)",i);
  db.update("INSERT INTO dbo.work_schedules VALUES(99,12,'2030-01-21 08:00:00','2030-01-21 17:00:00','2030-01-01 00:00:00'),(100,13,'2030-01-21 08:00:00','2030-01-21 17:00:00',NULL)");
  db.update("INSERT INTO dbo.contracts VALUES(1,12,'HD-1','2029-01-01',NULL,'DA_KY')");
  db.update("INSERT INTO dbo.employee_requests VALUES(1,12,'NP-1','DA_DUYET'),(2,12,'NP-2','DA_DUYET'),(3,12,'NP-3','CHO_DUYET')");
  db.update("INSERT INTO dbo.leave_request_details VALUES(1,'2030-01-21 08:00:00','2030-01-21 12:00:00'),(2,'2030-01-21 08:00:00','2030-01-21 12:00:00'),(3,'2030-01-21 08:00:00','2030-01-21 12:00:00')");
  db.update("INSERT INTO dbo.employee_request_withdrawals VALUES(2)");
  db.update("INSERT INTO dbo.attendance_records VALUES(1,12,'2030-01-21','DA_XAC_NHAN',NULL),(2,12,'2030-01-21','DA_XAC_NHAN','2030-01-22 00:00:00')");
  var issues=guard.inspect(12,last);assertEquals(7,issues.size());
  var schedules=issues.stream().filter(i->i.code().equals("SCHEDULE")).findFirst().orElseThrow();
  assertEquals(8,schedules.count());assertEquals(5,schedules.records().size());
  assertEquals(1,issues.stream().filter(i->i.code().equals("LEAVE")).findFirst().orElseThrow().count());
  var error=assertThrows(ResponseStatusException.class,()->guard.check(12,last));assertEquals(409,error.getStatusCode().value());assertTrue(error.getReason().contains("(8)"));
 }
 @Test void resolvedOrCancelledDataNoLongerBlocks(){
  db.update("INSERT INTO dbo.work_schedules VALUES(1,12,'2030-01-22 08:00:00','2030-01-22 17:00:00',NULL)");
  assertFalse(guard.inspect(12,last).isEmpty());
  db.update("UPDATE dbo.work_schedules SET cancelled_at='2030-01-01 00:00:00' WHERE schedule_id=1");
  assertTrue(guard.inspect(12,last).isEmpty());
 }
}
