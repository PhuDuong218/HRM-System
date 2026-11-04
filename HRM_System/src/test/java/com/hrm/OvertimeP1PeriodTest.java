package com.hrm;
import com.hrm.controller.OvertimeController;
import com.hrm.dto.OvertimeDTOs.*;
import com.hrm.security.AuthenticatedAccount;
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

class OvertimeP1PeriodTest {
 private final JdbcTemplate db=mock(JdbcTemplate.class);
 private final OvertimePermissionService perms=mock(OvertimePermissionService.class);
 private final AuthenticatedAccount actor=new AuthenticatedAccount(1L,10L,"admin","ADMIN");
 private final LocalDateTime start=LocalDateTime.now().minusDays(2).withHour(18).withMinute(0).withSecond(0).withNano(0),end=start.plusHours(2);
 private OvertimeService lockedCore(){
  var row=new OvertimeView(7L,20L,"OT-7",start,end,"reason","DA_DUYET",2L,null,start,end,0,null,null,null,null);
  doReturn(List.of(row)).when(db).query(contains("FROM dbo.overtime_requests o WHERE"),any(RowMapper.class),eq(7L));
  doReturn(List.of(20L)).when(db).query(contains("FROM dbo.employees WITH"),any(RowMapper.class),eq(20L));
  var admin=new OvertimePermissionService.VerifiedActor(1L,10L,"ADMIN");
  when(perms.requireOperate(actor,20L,start.toLocalDate())).thenReturn(admin);
  when(perms.requireConfirm(actor,20L,start.toLocalDate(),2L)).thenReturn(admin);
  when(db.queryForObject(contains("FROM dbo.payroll_periods"),eq(Long.class),any(),any())).thenReturn(1L);
  return new OvertimeService(db,perms,mock(OvertimeDailyLedger.class),mock(OvertimeRecognizedMinutes.class));
 }
 @Test void directCoreCannotRecordIntoLockedOrPublishedPeriod(){var core=lockedCore();var e=assertThrows(ResponseStatusException.class,()->core.recordActual(actor,7L,new Actual(start,end,0,List.of(),"reason")));assertEquals(409,e.getStatusCode().value());verify(db,never()).update(anyString(),any(Object[].class));}
 @Test void directCoreCannotConfirmLockedOrPublishedPeriod(){var core=lockedCore();assertThrows(ResponseStatusException.class,()->core.confirm(actor,7L));verify(db,never()).update(anyString(),any(Object[].class));}
 @Test void periodGuardChecksAllFinalityMarkers(){var core=lockedCore();assertThrows(ResponseStatusException.class,()->core.recordActual(actor,7L,new Actual(start,end,0,List.of(),"reason")));verify(db).queryForObject(argThat(sql->sql.contains("b.locked_at IS NOT NULL")&&sql.contains("b.published_at IS NOT NULL")&&sql.contains("b.status='DA_CHOT'")&&sql.contains("WITH(HOLDLOCK)")),eq(Long.class),eq(end.minusNanos(1).toLocalDate()),eq(start.toLocalDate()));}
 @Test void legacyActualEndpointUsesVersionedWorkspace(){var core=mock(OvertimeService.class);var workspace=mock(OvertimeWorkspaceService.class);var controller=new OvertimeController(core,workspace);var body=new Actual(start,end,0,List.of(),"reason");controller.actual(actor,7L,body,"\"AQID\"");verify(workspace).action(actor,7L,"actual",new OvertimeWorkspaceService.Mutation("AQID","reason",body));verifyNoInteractions(core);}
 @Test void cannotMoveActualIntervalFromOpenPeriodIntoLockedPeriod(){
  var core=lockedCore();var movedStart=start.minusMonths(1);var movedEnd=end.minusMonths(1);
  when(db.queryForObject(contains("FROM dbo.payroll_periods"),eq(Long.class),any(),any())).thenAnswer(call->movedStart.toLocalDate().equals(call.getArgument(3))?1L:0L);
  assertThrows(ResponseStatusException.class,()->core.recordActual(actor,7L,new Actual(movedStart,movedEnd,0,List.of(),"reason")));
  verify(db,never()).update(anyString(),any(Object[].class));
 }
}