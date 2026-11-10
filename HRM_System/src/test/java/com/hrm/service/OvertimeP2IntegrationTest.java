package com.hrm.service;
import com.hrm.dto.OvertimeDTOs.*;
import java.time.*;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
@EnabledIfSystemProperty(named="hrm.overtime.sqlserver-tests",matches="true")
class OvertimeP2IntegrationTest {
 static OvertimeWorkspaceIntegrationTest t;
 @BeforeAll static void setup()throws Exception{
  OvertimeWorkspaceIntegrationTest.setup();t=new OvertimeWorkspaceIntegrationTest();
  assertTrue(Set.of("hrm_db","hrm_test").contains(t.db.queryForObject("SELECT DB_NAME()",String.class)));
  // SQL tests only read the installed schema; all business fixtures roll back.
  assertNotNull(t.db.queryForObject("SELECT OBJECT_ID('dbo.attendance_actual_breaks','U')",Integer.class),"Apply migration 012 before running P2 SQL tests");
 }
 private long confirmedFixture(){long id=t.service.register(t.employee,t.body(20,19,21)).overtimeId();t.action(t.admin,id,"approve");var start=LocalDateTime.now(ZoneId.of("Asia/Ho_Chi_Minh")).minusDays(1).withHour(18).withMinute(0).withSecond(0).withNano(0);t.core.recordActual(t.admin,id,new Actual(start,start.plusHours(1),0,List.of(),"P2 rollback fixture"));t.db.update("UPDATE dbo.overtime_requests SET confirmed_by=?,confirmed_at=SYSUTCDATETIME() WHERE overtime_id=?",t.admin.accountId(),id);return id;}
 private String version(long id){return t.version(t.admin,id);}
 @Test void correctionCycleRequiresReopenFreshVersionAndReasonAndCannotRepeat(){t.rollback(()->{
  long id=confirmedFixture();var old=version(id);var row=t.core.get(t.admin,id).request();var actual=new Actual(row.actualStart(),row.actualEnd().minusMinutes(5),0,List.of(),"P2 documented amendment");
  assertThrows(ResponseStatusException.class,()->t.service.action(t.admin,id,"amend-actual",new OvertimeWorkspaceService.Mutation(old,actual.reason(),actual)));
  t.service.action(t.admin,id,"reopen",new OvertimeWorkspaceService.Mutation(old,"P2 reopen correction",null));
  assertTrue(t.core.amendmentOpen(id));var current=version(id);
  assertThrows(ResponseStatusException.class,()->t.workflows.requestOverflow(t.employee,id,"P2 overflow during correction",List.of()));
  assertThrows(ResponseStatusException.class,()->t.service.action(t.admin,id,"amend-actual",new OvertimeWorkspaceService.Mutation(old,actual.reason(),actual)));
  assertThrows(ResponseStatusException.class,()->t.core.recordActual(t.admin,id,actual));
  assertThrows(ResponseStatusException.class,()->t.core.confirm(t.admin,id));
  t.service.action(t.admin,id,"amend-actual",new OvertimeWorkspaceService.Mutation(current,actual.reason(),actual));
  assertFalse(t.core.amendmentOpen(id));assertTrue(t.core.actualRecorded(id));assertNull(t.core.get(t.admin,id).request().confirmedBy());
  assertThrows(ResponseStatusException.class,()->t.core.amendActual(t.admin,id,actual,version(id)));
  assertEquals(1L,t.db.queryForObject("SELECT COUNT_BIG(*) FROM dbo.audit_logs WHERE target_table='overtime_requests' AND target_record_key=? AND action='OT_ACTUAL_AMEND'",Long.class,Long.toString(id)));
 });}
 @Test void amendmentAuditFailureRollsBackActualAndLeavesCycleOpen(){t.rollback(()->{
  long id=confirmedFixture();t.service.action(t.admin,id,"reopen",new OvertimeWorkspaceService.Mutation(version(id),"P2 reopen",null));var before=t.core.get(t.admin,id).request();var v=version(id);
  JdbcTemplate failing=spy(t.db);doThrow(new DataIntegrityViolationException("P2 injected audit failure")).when(failing).update(startsWith("INSERT dbo.audit_logs("),any(Object[].class));
  var core=OvertimeWorkspaceIntegrationTest.proxy(new OvertimeService(failing,t.perms,t.ledger,t.recognition));var nested=new TransactionTemplate(t.manager);nested.setPropagationBehavior(TransactionDefinition.PROPAGATION_NESTED);
  assertThrows(DataIntegrityViolationException.class,()->nested.execute(status->core.amendActual(t.admin,id,new Actual(before.actualStart(),before.actualEnd().minusMinutes(5),0,List.of(),"P2 amendment"),v)));
  assertEquals(before.actualEnd(),t.core.get(t.admin,id).request().actualEnd());assertTrue(t.core.amendmentOpen(id));
 });}
 @Test void ordinaryApprovedWindowCanCompleteReopenAmendAndReconfirm(){t.rollback(()->{
  var request=t.body(-1,22,23);long id=t.service.register(t.employee,request).overtimeId();t.action(t.admin,id,"approve");
  var start=request.requestedStart().plusMinutes(5);var end=request.requestedEnd().minusMinutes(5);
  t.core.recordActual(t.admin,id,new Actual(start,end,0,List.of(),"P2 original record"));t.core.confirm(t.admin,id);
  t.service.action(t.admin,id,"reopen",new OvertimeWorkspaceService.Mutation(version(id),"P2 documented correction",null));
  var amended=new Actual(start.plusMinutes(5),end.minusMinutes(5),0,List.of(),"P2 corrected evidence");
  t.service.action(t.admin,id,"amend-actual",new OvertimeWorkspaceService.Mutation(version(id),amended.reason(),amended));t.core.confirm(t.admin,id);
  assertNotNull(t.core.get(t.admin,id).request().confirmedBy());assertFalse(t.core.amendmentOpen(id));
 });}
}