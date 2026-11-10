package com.hrm.service;
import java.security.Principal;
import java.time.LocalDate;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static org.junit.jupiter.api.Assertions.*;

class ResignationReadinessAccessTest {
 @Test void revokedScopeDeniesBeforeAnyBlockerDetailsAreRead(){
  var db=mock(JdbcTemplate.class);var access=mock(PersonnelAccessService.class);var support=mock(PersonnelWorkflowSupport.class);var scope=mock(PersonnelScopeService.class);var exit=mock(EmploymentExitGuard.class);
  var service=new EmployeeRequestService(db,access,support,mock(AssignmentGuardService.class));
  ReflectionTestUtils.setField(service,"personnelScope",scope);ReflectionTestUtils.setField(service,"exitGuard",exit);
  Principal p=()->"hr";when(access.approver(p)).thenReturn(new PersonnelAccessService.Actor(1,100,"HR"));
  when(db.queryForList(anyString(),eq(9L))).thenReturn(List.of(new HashMap<>(Map.of("employeeId",12L,"requestType","NGHI_VIEC"))));
  doThrow(new ResponseStatusException(org.springframework.http.HttpStatus.FORBIDDEN,"Ngoài phạm vi")).when(scope).managedEmployee(12);
  var error=assertThrows(ResponseStatusException.class,()->service.resignationReadiness(9,LocalDate.of(2030,1,20),p));
  assertEquals(403,error.getStatusCode().value());verifyNoInteractions(exit);
 }
 @Test void pendingHeadApprovalCannotBecomeReadyEvenWithoutDataBlockers(){
  var db=mock(JdbcTemplate.class);var access=mock(PersonnelAccessService.class);var support=mock(PersonnelWorkflowSupport.class);var exit=mock(EmploymentExitGuard.class);var stages=mock(RequestApprovalStages.class);
  var service=new EmployeeRequestService(db,access,support,mock(AssignmentGuardService.class));
  ReflectionTestUtils.setField(service,"personnelScope",mock(PersonnelScopeService.class));ReflectionTestUtils.setField(service,"exitGuard",exit);ReflectionTestUtils.setField(service,"approvalStages",stages);
  Principal p=()->"admin";when(access.approver(p)).thenReturn(new PersonnelAccessService.Actor(1,100,"ADMIN"));
  var row=new HashMap<String,Object>(Map.of("employeeId",12L,"requestType","NGHI_VIEC","status","CHO_DUYET","requestedLastWorkingDate","2030-01-20"));
  when(db.queryForList(anyString(),eq(9L))).thenReturn(List.of(row));when(db.queryForMap(anyString(),eq(12L))).thenReturn(Map.of("employee_code","NV012","full_name","Lê Minh","employment_status","DANG_LAM"));
  when(support.today()).thenReturn(LocalDate.of(2030,1,1));when(stages.finalApprovalBlocker(9,12)).thenReturn("Cần duyệt cấp 1");when(exit.inspect(anyLong(),any())).thenReturn(List.of());
  var result=service.resignationReadiness(9,null,p);assertFalse(result.canApprove());assertEquals("Cần duyệt cấp 1",result.approvalMessage());
  verify(db,never()).update(anyString(),any(Object[].class));
 }
}
