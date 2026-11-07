package com.hrm.service;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.web.server.ResponseStatusException;
import static org.junit.jupiter.api.Assertions.*;
@EnabledIfSystemProperty(named="hrm.overtime.sqlserver-tests",matches="true")
class PayrollReadinessGuardIntegrationTest {
 static OvertimeWorkspaceIntegrationTest t;
 static PayrollReadinessGuard guard;
 static LocalDate date=LocalDate.of(1990,1,1);
 @BeforeAll static void setup()throws Exception{OvertimeWorkspaceIntegrationTest.setup();t=new OvertimeWorkspaceIntegrationTest();guard=new PayrollReadinessGuard(t.db,Clock.fixed(Instant.parse("1990-02-03T03:00:00Z"),ZoneId.of("Asia/Ho_Chi_Minh")));}
 long period(){return t.db.queryForObject("INSERT dbo.payroll_periods(period_code,period_start,period_end) OUTPUT INSERTED.period_id VALUES(?,?,?)",Long.class,"GUARD-"+UUID.randomUUID().toString().substring(0,12),date,date.plusDays(1));}
 long schedule(){return t.db.queryForObject("INSERT dbo.work_schedules(employee_id,shift_id,work_date,planned_start,planned_end,planned_break_minutes,note) OUTPUT INSERTED.schedule_id VALUES(?,NULL,?,?,?,0,'rollback-only payroll guard')",Long.class,t.employee.employeeId(),date,date.atTime(8,0),date.atTime(17,0));}
 @Test void completeEmptyPeriodPasses(){t.rollback(()->guard.requireReady(period()));}
 @Test void missingAndUnconfirmedAttendanceBlockUntilConfirmed(){t.rollback(()->{
  long period=period(),sid=schedule();assertTrue(assertThrows(ResponseStatusException.class,()->guard.requireReady(period)).getReason().contains("lịch/công"));
  long id=t.db.queryForObject("INSERT dbo.attendance_records(employee_id,schedule_id,work_date,actual_start,actual_end,actual_break_minutes,attendance_result,verification_status,created_by) OUTPUT INSERTED.attendance_id VALUES(?,?,?,?,?,0,'DI_LAM','CHUA_XAC_NHAN',?)",Long.class,t.employee.employeeId(),sid,date,date.atTime(8,0),date.atTime(17,0),t.admin.accountId());
  assertThrows(ResponseStatusException.class,()->guard.requireReady(period));
  t.db.update("UPDATE dbo.attendance_records SET verification_status='DA_XAC_NHAN',confirmed_by=?,confirmed_at=SYSUTCDATETIME() WHERE attendance_id=?",t.admin.accountId(),id);
  guard.requireReady(period);
 });}
 @Test void pendingOvertimeBlocksEvenWithoutRegularSchedule(){t.rollback(()->{
  long period=period();t.db.update("INSERT dbo.overtime_requests(employee_id,request_code,requested_start,requested_end,reason,created_by) VALUES(?,?,?,?,?,?)",t.employee.employeeId(),"GUARD-"+UUID.randomUUID().toString().substring(0,12),date.atTime(18,0),date.atTime(20,0),"rollback-only",t.employee.accountId());
  assertTrue(assertThrows(ResponseStatusException.class,()->guard.requireReady(period)).getReason().contains("OT chưa duyệt"));
 });}
 @Test void requiresCallerTransaction(){assertThrows(IllegalStateException.class,()->guard.requireReady(1));}
}
