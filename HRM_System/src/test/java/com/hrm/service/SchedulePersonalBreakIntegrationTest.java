package com.hrm.service;
import java.time.*;
import java.util.*;
import com.hrm.dto.DetailedWorkTimeDTOs.*;
import com.hrm.dto.WorkTimeDTOs.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import static org.junit.jupiter.api.Assertions.*;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.json.JsonMapper;
@EnabledIfSystemProperty(named="hrm.overtime.sqlserver-tests",matches="true")
class SchedulePersonalBreakIntegrationTest {
 static OvertimeWorkspaceIntegrationTest t;static ScheduleWorkspaceService workspace;static WorkShiftManagementWriteService shifts;
 @BeforeAll static void setup()throws Exception{OvertimeWorkspaceIntegrationTest.setup();t=new OvertimeWorkspaceIntegrationTest();var shiftService=new WorkShiftService(t.db,new WorkShiftPermissionService(t.db));var permissions=new ScheduleWritePermissionService(t.db);var schedules=OvertimeWorkspaceIntegrationTest.proxy(new WorkScheduleService(t.db,permissions));var detailed=OvertimeWorkspaceIntegrationTest.proxy(new DetailedWorkTimeService(t.db,shiftService,schedules));workspace=OvertimeWorkspaceIntegrationTest.proxy(new ScheduleWorkspaceService(t.db,shiftService,schedules,new ScheduleQueryService(t.db),permissions,detailed,JsonMapper.builder().build()));shifts=OvertimeWorkspaceIntegrationTest.proxy(new WorkShiftManagementWriteService(t.db,new WorkShiftPermissionService(t.db),shiftService,detailed,JsonMapper.builder().build()));}
 private ShiftView configureLegacy(){long id=t.db.queryForObject("SELECT shift_id FROM dbo.work_shifts WHERE shift_code='HANH_CHINH'",Long.class);return shifts.update(t.admin,id,new ShiftWithBreaks(new ShiftInput("HANH_CHINH","Ca hành chính",LocalTime.of(8,0),LocalTime.of(17,0),0,60,true),List.of(new OffsetBreak(240,300))));}
 @Test void configuringActualRestAllowsEmployeeScheduleCreation(){t.rollback(()->{var shift=configureLegacy();var result=workspace.save(t.admin,null,new ScheduleWorkspaceService.SaveInput(t.employee.employeeId(),shift.shiftId(),t.day(22),"ROLLBACK-ONLY schedule"));assertEquals(60,result.plannedBreakMinutes());assertEquals(1L,t.db.queryForObject("SELECT COUNT_BIG(*) FROM dbo.work_schedule_breaks WHERE schedule_id=? AND superseded_at IS NULL",Long.class,result.scheduleId()));});}
 @Test void hrPersonalScheduleDoesNotDependOnManagementDepartmentScope(){t.rollback(()->{var shift=configureLegacy();var result=workspace.save(t.admin,null,new ScheduleWorkspaceService.SaveInput(t.hr.employeeId(),shift.shiftId(),t.day(22),"ROLLBACK-ONLY HR personal"));var page=workspace.mine(t.hr,null,t.day(22),t.day(22),0);assertTrue(page.items().stream().anyMatch(r->r.schedule().scheduleId()==result.scheduleId()));assertTrue(page.items().stream().allMatch(r->r.schedule().employeeId()==t.hr.employeeId()));assertThrows(ResponseStatusException.class,()->workspace.mine(t.admin,null,t.day(22),t.day(22),0));});}
}
