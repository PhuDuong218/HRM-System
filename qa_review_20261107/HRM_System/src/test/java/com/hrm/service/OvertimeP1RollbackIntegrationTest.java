package com.hrm.service;

import com.hrm.dto.OvertimeDTOs.*;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Uses real SQL Server transactions; every fixture is rolled back. */
@EnabledIfSystemProperty(named="hrm.overtime.sqlserver-tests",matches="true")
class OvertimeP1RollbackIntegrationTest {
 static OvertimeWorkspaceIntegrationTest t;
 @BeforeAll static void setup() throws Exception {OvertimeWorkspaceIntegrationTest.setup();t=new OvertimeWorkspaceIntegrationTest();}
 @Test void auditFailureRollsBackActualAndBreaksTogether(){t.rollback(()->{
  long id=t.service.register(t.employee,t.body(20,19,21)).overtimeId();
  t.action(t.admin,id,"approve");
  var before=t.core.get(t.admin,id).request();
  JdbcTemplate failing=spy(t.db);
  doThrow(new DataIntegrityViolationException("P1 injected audit failure")).when(failing).update(startsWith("INSERT dbo.audit_logs("),any(Object[].class));
  var core=OvertimeWorkspaceIntegrationTest.proxy(new OvertimeService(failing,t.perms,t.ledger,t.recognition));
  LocalDateTime start=LocalDateTime.now(ZoneId.of("Asia/Ho_Chi_Minh")).minusDays(1).withHour(18).withMinute(0).withSecond(0).withNano(0);
  var rests=List.of(new BreakInput(start.plusMinutes(1),start.plusMinutes(1).plusSeconds(59)),new BreakInput(start.plusMinutes(3),start.plusMinutes(3).plusSeconds(59)));
  var nested=new TransactionTemplate(t.manager);nested.setPropagationBehavior(TransactionDefinition.PROPAGATION_NESTED);
  assertThrows(DataIntegrityViolationException.class,()->nested.execute(status->core.recordActual(t.admin,id,new Actual(start,start.plusMinutes(30),1,rests,"P1 rollback verification"))));
  var after=t.core.get(t.admin,id).request();assertEquals(before.actualStart(),after.actualStart());assertEquals(before.actualEnd(),after.actualEnd());assertEquals(before.actualBreakMinutes(),after.actualBreakMinutes());
  assertEquals(0L,t.db.queryForObject("SELECT COUNT_BIG(*) FROM dbo.overtime_breaks WHERE overtime_id=?",Long.class,id));
  assertEquals(0L,t.db.queryForObject("SELECT COUNT_BIG(*) FROM dbo.audit_logs WHERE target_table='overtime_requests' AND target_record_key=? AND action='OT_ACTUAL'",Long.class,Long.toString(id)));
 });}
 @Test void successfulActualRecordUsesOneAggregateFloorAndKeepsAudit(){t.rollback(()->{
  long id=t.service.register(t.employee,t.body(20,19,21)).overtimeId();t.action(t.admin,id,"approve");
  LocalDateTime start=LocalDateTime.now(ZoneId.of("Asia/Ho_Chi_Minh")).minusDays(1).withHour(18).withMinute(0).withSecond(0).withNano(0);
  var rests=List.of(new BreakInput(start.plusMinutes(1),start.plusMinutes(1).plusSeconds(59)),new BreakInput(start.plusMinutes(3),start.plusMinutes(3).plusSeconds(59)));
  var result=t.core.recordActual(t.admin,id,new Actual(start,start.plusMinutes(30),1,rests,"P1 exact break aggregation"));
  assertEquals(1,result.actualBreakMinutes());
  assertEquals(28L,t.ledger.netMinutes(id,result.actualStart(),result.actualEnd(),result.actualBreakMinutes()).get(start.toLocalDate()));
  assertEquals(1L,t.db.queryForObject("SELECT COUNT_BIG(*) FROM dbo.audit_logs WHERE target_table='overtime_requests' AND target_record_key=? AND action='OT_ACTUAL'",Long.class,Long.toString(id)));
 });}
}