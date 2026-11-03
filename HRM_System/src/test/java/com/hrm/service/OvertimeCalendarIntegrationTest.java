package com.hrm.service;
import java.time.*;
import java.util.*;
import com.hrm.dto.OvertimeDTOs.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import static org.junit.jupiter.api.Assertions.*;
import org.springframework.web.server.ResponseStatusException;

/** Real SQL Server, explicit opt-in, every fixture and clock event rolls back. */
@EnabledIfSystemProperty(named="hrm.overtime.sqlserver-tests",matches="true")
class OvertimeCalendarIntegrationTest {
 static OvertimeWorkspaceIntegrationTest t;
 @BeforeAll static void setup()throws Exception{OvertimeWorkspaceIntegrationTest.setup();t=new OvertimeWorkspaceIntegrationTest();}
 private OvertimeCalendarService at(LocalDateTime time){return OvertimeWorkspaceIntegrationTest.proxy(new OvertimeCalendarService(t.db,t.service,t.perms,Clock.fixed(time.atZone(ZoneId.of("Asia/Ho_Chi_Minh")).toInstant(),ZoneId.of("Asia/Ho_Chi_Minh"))));}
 private long create(boolean approve){var o=t.service.register(t.employee,t.body(20,19,21));if(approve)t.action(t.admin,o.overtimeId(),"approve");return o.overtimeId();}
 private OvertimeCalendarService.ClockInput input(long id,boolean noRest){return new OvertimeCalendarService.ClockInput(t.version(t.employee,id),List.of(),noRest);}
 @Test void approvalProjectsIntoBothCalendarsWithoutCreatingRegularWork(){t.rollback(()->{
  Long count=t.db.queryForObject("SELECT COUNT_BIG(*) FROM dbo.work_schedules",Long.class);long id=create(false);var service=at(t.day(20).atTime(19,0));
  assertTrue(service.calendar(t.employee,true,t.day(20),t.day(20),null,null,0).items().stream().noneMatch(i->i.row().ot().overtimeId()==id));
  t.action(t.admin,id,"approve");assertTrue(service.calendar(t.employee,true,t.day(20),t.day(20),null,null,0).items().stream().anyMatch(i->i.row().ot().overtimeId()==id&&i.canCheckIn()));
  assertTrue(service.calendar(t.admin,false,t.day(20),t.day(20),null,null,0).items().stream().anyMatch(i->i.row().ot().overtimeId()==id&&!i.canCheckIn()));
  assertEquals(count,t.db.queryForObject("SELECT COUNT_BIG(*) FROM dbo.work_schedules",Long.class));
 });}
 @Test void selfClockUsesServerTimeAndLeavesActualUnconfirmed(){t.rollback(()->{long id=create(true);var in=t.day(20).atTime(19,1);var out=t.day(20).atTime(20,1);at(in).clock(t.employee,id,true,input(id,false));var row=at(out).clock(t.employee,id,false,input(id,true)).row();assertEquals(in,row.ot().actualStart());assertEquals(out,row.ot().actualEnd());assertNull(row.ot().confirmedBy());assertEquals(0,row.ot().actualBreakMinutes());assertEquals(2L,t.db.queryForObject("SELECT COUNT_BIG(*) FROM dbo.audit_logs WHERE target_table='overtime_requests' AND target_record_key=? AND action IN ('OT_SELF_CHECK_IN','OT_SELF_CHECK_OUT')",Long.class,Long.toString(id)));});}
 @Test void refusesEarlyCheckInAndWrongOwner(){t.rollback(()->{long id=create(true);assertThrows(ResponseStatusException.class,()->at(t.day(20).atTime(18,29)).clock(t.employee,id,true,input(id,false)));assertThrows(ResponseStatusException.class,()->at(t.day(20).atTime(19,0)).clock(t.admin,id,true,input(id,false)));assertThrows(ResponseStatusException.class,()->at(t.day(20).atTime(19,0)).clock(t.hr,id,true,input(id,false)));assertNull(t.service.detail(t.employee,id).row().ot().actualStart());});}
 @Test void refusesStaleVersionDuplicateCheckInAndUnspecifiedBreaks(){t.rollback(()->{long id=create(true);var stale=input(id,false);at(t.day(20).atTime(19,0)).clock(t.employee,id,true,stale);assertThrows(ResponseStatusException.class,()->at(t.day(20).atTime(20,0)).clock(t.employee,id,false,stale));assertThrows(ResponseStatusException.class,()->at(t.day(20).atTime(19,1)).clock(t.employee,id,true,input(id,false)));assertThrows(ResponseStatusException.class,()->at(t.day(20).atTime(20,0)).clock(t.employee,id,false,input(id,false)));assertNull(t.service.detail(t.employee,id).row().ot().actualEnd());});}
 @Test void checkoutStoresActualDetailedRests(){t.rollback(()->{long id=create(true);at(t.day(20).atTime(19,0)).clock(t.employee,id,true,input(id,false));var rest=new BreakInput(t.day(20).atTime(19,10),t.day(20).atTime(19,20));var row=at(t.day(20).atTime(20,0)).clock(t.employee,id,false,new OvertimeCalendarService.ClockInput(t.version(t.employee,id),List.of(rest),false)).row();assertEquals(10,row.ot().actualBreakMinutes());assertEquals(1L,t.db.queryForObject("SELECT COUNT_BIG(*) FROM dbo.overtime_breaks WHERE overtime_id=? AND is_active=1",Long.class,id));});}
 @Test void cancelledOtDisappearsFromCalendar(){t.rollback(()->{long id=create(true);long request=t.service.requestCancellation(t.employee,id,t.version(t.employee,id),"ROLLBACK-ONLY hủy lịch OT");t.service.decideRequest(t.admin,request,true,null,List.of());assertTrue(at(t.day(20).atTime(19,0)).calendar(t.employee,true,t.day(20),t.day(20),null,null,0).items().stream().noneMatch(i->i.row().ot().overtimeId()==id));});}
}
