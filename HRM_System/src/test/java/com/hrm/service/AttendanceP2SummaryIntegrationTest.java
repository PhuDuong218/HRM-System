package com.hrm.service;
import java.time.*;
import java.util.*;
import java.nio.file.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import static org.junit.jupiter.api.Assertions.*;
@EnabledIfSystemProperty(named="hrm.overtime.sqlserver-tests",matches="true")
class AttendanceP2SummaryIntegrationTest {
 static OvertimeWorkspaceIntegrationTest t;static AttendanceActualBreakService actual;static ConfirmedAttendancePeriodService summary;
 private final LocalDateTime start=LocalDateTime.parse("2026-10-31T22:00:00"),end=LocalDateTime.parse("2026-11-01T06:00:00");
 @BeforeAll static void setup()throws Exception{OvertimeWorkspaceIntegrationTest.setup();t=new OvertimeWorkspaceIntegrationTest();assertTrue(Set.of("hrm_db","hrm_test").contains(t.db.queryForObject("SELECT DB_NAME()",String.class)));assertNotNull(t.db.queryForObject("SELECT OBJECT_ID('dbo.attendance_actual_breaks','U')",Integer.class),"Apply migration 012 before running P2 SQL tests");actual=new AttendanceActualBreakService(t.db,null);summary=new ConfirmedAttendancePeriodService(t.db,actual);}
 private long fixture(int minutes){return fixture(minutes,true);}
 private long fixture(int minutes,boolean confirmed){
  long schedule=t.db.queryForObject("SET NOCOUNT ON;DECLARE @ids TABLE(id bigint);INSERT dbo.work_schedules(employee_id,shift_id,work_date,planned_start,planned_end,planned_break_minutes,note) OUTPUT INSERTED.schedule_id INTO @ids VALUES(?,NULL,?,?,?,0,N'P2 rollback-only night shift');SELECT id FROM @ids;",Long.class,t.employee.employeeId(),start.toLocalDate(),start,end);
  return t.db.queryForObject("SET NOCOUNT ON;DECLARE @ids TABLE(id bigint);INSERT dbo.attendance_records(employee_id,schedule_id,work_date,actual_start,actual_end,actual_break_minutes,attendance_result,verification_status,created_by,confirmed_by,confirmed_at) OUTPUT INSERTED.attendance_id INTO @ids VALUES(?,?,?,?,?,?,'DI_LAM',?,?,?,?);SELECT id FROM @ids;",Long.class,t.employee.employeeId(),schedule,start.toLocalDate(),start,end,minutes,confirmed?"DA_XAC_NHAN":"CHUA_XAC_NHAN",t.admin.accountId(),confirmed?t.admin.accountId():null,confirmed?LocalDateTime.now(ZoneOffset.UTC):null);
 }
 private ConfirmedAttendancePeriodService.PeriodSummary period(int month){var first=LocalDate.of(2026,month,1);return summary.summarize(t.admin,first,first.plusMonths(1).minusDays(1),t.employee.employeeId());}
 @Test void monthBoundaryIncludesCarryInAndVerifiedBreakDetails(){t.rollback(()->{
  long oct=period(10).netMinutes(),nov=period(11).netMinutes();long id=fixture(30);
  t.db.update("INSERT dbo.attendance_actual_breaks(attendance_id,break_start,break_end,source_start,source_end,source_break_minutes,created_by) VALUES(?,?,?,?,?,?,?)",id,start.plusHours(1).plusMinutes(50),end.minusHours(5).minusMinutes(40),start,end,30,t.admin.accountId());
  assertEquals(oct+110,period(10).netMinutes());assertEquals(nov+340,period(11).netMinutes());
 });}
 @Test void zeroBreakNightShiftNeedsNoInventedRest(){t.rollback(()->{long oct=period(10).netMinutes(),nov=period(11).netMinutes();fixture(0);assertEquals(oct+120,period(10).netMinutes());assertEquals(nov+360,period(11).netMinutes());});}
 @Test void missingPositiveRestDetailsRemainUnresolved(){t.rollback(()->{var before=period(11);fixture(30);var after=period(11);assertEquals(before.netMinutes(),after.netMinutes());assertEquals(before.unresolvedRecords()+1,after.unresolvedRecords());});}
 @Test void changedActualTimesInvalidateOldRestVerification(){t.rollback(()->{long id=fixture(30);t.db.update("INSERT dbo.attendance_actual_breaks(attendance_id,break_start,break_end,source_start,source_end,source_break_minutes,created_by) VALUES(?,?,?,?,?,?,?)",id,start.plusHours(1),start.plusHours(1).plusMinutes(30),start,end,30,t.admin.accountId());assertNotNull(actual.verified(id,start,end,30));assertNull(actual.verified(id,start,end.plusMinutes(1),30));assertNull(actual.verified(id,start,end,31));});}
 private AttendancePortalService portal(){var permission=new AttendancePermissionService(t.db);return OvertimeWorkspaceIntegrationTest.proxy(new AttendancePortalService(t.db,permission,OvertimeWorkspaceIntegrationTest.proxy(new AttendanceService(t.db,permission)),new AttendanceSelfClockService(t.db,t.clock),t.clock,7,3));}
 private List<AttendanceActualBreakService.Break> proposedRests(){return List.of(new AttendanceActualBreakService.Break(start.plusHours(1).plusMinutes(50),end.minusHours(5).minusMinutes(40)));}
 private long schedule(long attendance){return t.db.queryForObject("SELECT schedule_id FROM dbo.attendance_records WHERE attendance_id=?",Long.class,attendance);}
 @Test void managerCanSaveActualRestsButStaleVersionAndOwnerCannot(){t.rollback(()->{
  long id=fixture(0,false);var portal=portal();var service=OvertimeWorkspaceIntegrationTest.proxy(new AttendanceActualBreakService(t.db,portal));var before=portal.detail(t.admin,false,schedule(id));
  var body=new AttendanceActualBreakService.Save(before.row().version(),proposedRests(),"P2 verify actual night breaks");
  org.junit.jupiter.api.Assertions.assertThrows(org.springframework.web.server.ResponseStatusException.class,()->service.save(t.employee,id,body));
  assertTrue(service.save(t.admin,id,body).verified());assertEquals(30,portal.detail(t.admin,false,schedule(id)).row().actualBreakMinutes());
  org.junit.jupiter.api.Assertions.assertThrows(org.springframework.web.server.ResponseStatusException.class,()->service.save(t.admin,id,body));
 });}
 @Test void actualRestAuditFailureRollsBackParentAndChildren(){t.rollback(()->{
  long id=fixture(0,false);var portal=portal();var before=portal.detail(t.admin,false,schedule(id));
  var failing=org.mockito.Mockito.spy(t.db);
  org.mockito.Mockito.doThrow(new org.springframework.dao.DataIntegrityViolationException("P2 actual rest audit failure")).when(failing).update(org.mockito.ArgumentMatchers.startsWith("INSERT dbo.audit_logs("),org.mockito.ArgumentMatchers.any(Object[].class));
  var service=OvertimeWorkspaceIntegrationTest.proxy(new AttendanceActualBreakService(failing,portal));
  var nested=new org.springframework.transaction.support.TransactionTemplate(t.manager);nested.setPropagationBehavior(org.springframework.transaction.TransactionDefinition.PROPAGATION_NESTED);
  assertThrows(org.springframework.dao.DataIntegrityViolationException.class,()->nested.execute(status->service.save(t.admin,id,new AttendanceActualBreakService.Save(before.row().version(),proposedRests(),"P2 rollback proof"))));
  assertEquals(0,portal.detail(t.admin,false,schedule(id)).row().actualBreakMinutes());assertEquals(0L,t.db.queryForObject("SELECT COUNT_BIG(*) FROM dbo.attendance_actual_breaks WHERE attendance_id=?",Long.class,id));
 });}
}