package com.hrm.service;
import java.time.LocalDate;
import java.security.Principal;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class SeniorityReportServiceTest {
 @Test void countsCalendarYearsAndMonths(){var t=SeniorityReportService.calculate(LocalDate.of(2020,1,15),null,LocalDate.of(2026,11,10));assertEquals(6,t.years());assertEquals(9,t.months());assertEquals(81,t.totalMonths());}
 @Test void stopsAtTermination(){assertEquals(12,SeniorityReportService.calculate(LocalDate.of(2020,1,1),LocalDate.of(2021,1,1),LocalDate.of(2026,11,10)).totalMonths());}
 @Test void handlesLeapDayAndFutureHire(){assertEquals(11,SeniorityReportService.calculate(LocalDate.of(2024,2,29),null,LocalDate.of(2025,2,28)).totalMonths());assertEquals(0,SeniorityReportService.calculate(LocalDate.of(2027,1,1),null,LocalDate.of(2026,1,1)).totalMonths());}
 @Test void searchesVietnameseWithoutAccents(){assertTrue(SeniorityReportService.normalize("NV01 Đặng Thị Ánh").contains("dang thi anh"));}
 @Test void deniesEmployeeBeforeReadingData(){var db=mock(JdbcTemplate.class);var access=mock(PersonnelAccessService.class);Principal p=()->"employee";when(access.manager(p)).thenThrow(new org.springframework.security.access.AccessDeniedException("Denied"));var service=new SeniorityReportService(db,access,mock(PersonnelScopeService.class),mock(PersonnelWorkflowSupport.class));assertThrows(org.springframework.security.access.AccessDeniedException.class,()->service.report(null,"",0,10,p));verifyNoInteractions(db);}
 @Test void hrScopeIsAppliedBeforeReadingEmployees(){
  var db=mock(JdbcTemplate.class);var access=mock(PersonnelAccessService.class);var scope=mock(PersonnelScopeService.class);var clock=mock(PersonnelWorkflowSupport.class);Principal p=()->"hr";var date=LocalDate.of(2026,11,10);
  when(access.manager(p)).thenReturn(new PersonnelAccessService.Actor(2,4,"HR"));when(clock.today()).thenReturn(date);when(scope.sqlEmployees("e.employee_id")).thenReturn("e.employee_id IN (12)");
  var service=new SeniorityReportService(db,access,scope,clock);service.report(null,"",0,10,p);
  verify(db).query(contains("AND e.employee_id IN (12)"),org.mockito.ArgumentMatchers.<org.springframework.jdbc.core.RowMapper<SeniorityReportService.Row>>any(),eq(date));
 }
 @Test void futureReportDateIsRejected(){var db=mock(JdbcTemplate.class);var access=mock(PersonnelAccessService.class);var clock=mock(PersonnelWorkflowSupport.class);Principal p=()->"admin";when(access.manager(p)).thenReturn(new PersonnelAccessService.Actor(1,1,"ADMIN"));when(clock.today()).thenReturn(LocalDate.of(2026,11,10));var service=new SeniorityReportService(db,access,mock(PersonnelScopeService.class),clock);assertThrows(org.springframework.web.server.ResponseStatusException.class,()->service.report(LocalDate.of(2026,11,11),"",0,10,p));verifyNoInteractions(db);}
}
