package com.hrm.service;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import static org.junit.jupiter.api.Assertions.*;
import java.time.*;
import java.util.*;
@EnabledIfSystemProperty(named="hrm.overtime.sqlserver-tests",matches="true")
class ScheduleSearchSortIntegrationTest {
 static OvertimeWorkspaceIntegrationTest t;static ScheduleWorkspaceService service;
 @BeforeAll static void setup()throws Exception{SchedulePersonalBreakIntegrationTest.setup();t=SchedulePersonalBreakIntegrationTest.t;service=SchedulePersonalBreakIntegrationTest.workspace;}
 @Test void partialAccentlessEmployeeSuggestions(){t.rollback(()->{t.db.update("UPDATE dbo.employees SET full_name=N'Châu Thành Đạt' WHERE employee_id=?",t.employee.employeeId());for(String q:List.of("cha","CHÂU","thanh","đạt","dat",t.db.queryForObject("SELECT employee_code FROM dbo.employees WHERE employee_id=?",String.class,t.employee.employeeId()).substring(0,3)))assertTrue(service.employees(t.admin,q,LocalDate.now(t.clock)).stream().anyMatch(e->e.employeeId()==t.employee.employeeId()),q);});}
 @Test void idSortAppliesBeforePagination(){t.rollback(()->{LocalDate from=LocalDate.of(2020,1,1),to=LocalDate.of(2100,12,31);var desc=service.searchSorted(t.admin,null,null,null,from,to,0,"desc");var asc=service.searchSorted(t.admin,null,null,null,from,to,0,"asc");assertFalse(desc.items().isEmpty());for(int i=1;i<desc.items().size();i++)assertTrue(desc.items().get(i-1).schedule().scheduleId()>desc.items().get(i).schedule().scheduleId());for(int i=1;i<asc.items().size();i++)assertTrue(asc.items().get(i-1).schedule().scheduleId()<asc.items().get(i).schedule().scheduleId());assertEquals(t.db.queryForObject("SELECT MAX(schedule_id) FROM dbo.work_schedules",Long.class),desc.items().get(0).schedule().scheduleId());assertEquals(t.db.queryForObject("SELECT MIN(schedule_id) FROM dbo.work_schedules",Long.class),asc.items().get(0).schedule().scheduleId());if(desc.totalPages()>1){var next=service.searchSorted(t.admin,null,null,null,from,to,1,"desc");assertTrue(desc.items().get(desc.items().size()-1).schedule().scheduleId()>next.items().get(0).schedule().scheduleId());}});}
}
