package com.hrm.service;
import com.hrm.dto.EmployeeContactPatchDTO;
import com.hrm.model.Employee;
import com.hrm.repository.EmployeeRepository;
import com.hrm.security.AuthenticatedAccount;
import org.junit.jupiter.api.Test;
import java.util.Optional;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;
class OwnProfileContactTest {
 final EmployeeRepository repository=mock(EmployeeRepository.class);
 final EmployeeService employees=mock(EmployeeService.class);
 final DepartmentHeadAccessService access=mock(DepartmentHeadAccessService.class);
 final EmployeeAuditService audit=mock(EmployeeAuditService.class);
 final EmployeeContactService service=new EmployeeContactService(repository,employees,access,audit);
 @Test void usesAuthenticatedEmployeeAndWritesAudit(){
  Employee employee=new Employee();employee.setRowVersion(new byte[8]);
  when(repository.findByIdForUpdate(8L)).thenReturn(Optional.of(employee));
  var request=new EmployeeContactPatchDTO();request.setVersion("0000000000000000");request.setReason("Self update");request.setEmail("new@example.com");
  service.updateOwnContact(new AuthenticatedAccount(12L,8L,"emp","EMPLOYEE"),request);
  verify(employees).patchEmployeeContact(8L,request);
  verify(audit).recordContactUpdate(eq(12L),eq(8L),anyMap(),anyMap(),eq("Self update"));
  verifyNoInteractions(access);
 }
 @Test void webRolesCanUpdateOnlyTheirOwnContact(){
  for(String role:java.util.List.of("ADMIN","HR")){Employee employee=new Employee();employee.setRowVersion(new byte[8]);when(repository.findByIdForUpdate(8L)).thenReturn(Optional.of(employee));var request=new EmployeeContactPatchDTO();request.setVersion("0000000000000000");request.setReason("Self update");request.setAddress("New address");service.updateOwnContact(new AuthenticatedAccount(12L,8L,"web",role),request);}
  verify(employees,times(2)).patchEmployeeContact(eq(8L),any());
 }
 @Test void rejectsIdentityChanges(){
  var request=new EmployeeContactPatchDTO();request.setVersion("0000000000000000");request.setReason("Self update");request.setIdentityNumber("123");
  assertThrows(org.springframework.web.server.ResponseStatusException.class,()->service.updateOwnContact(new AuthenticatedAccount(12L,8L,"emp","EMPLOYEE"),request));
  verifyNoInteractions(repository,employees,audit);
 }
 @Test void rejectsNonEmployeeAndAnonymous(){
  assertThrows(org.springframework.web.server.ResponseStatusException.class,()->service.updateOwnContact(new AuthenticatedAccount(12L,8L,"other","UNKNOWN"),new EmployeeContactPatchDTO()));
  assertThrows(org.springframework.web.server.ResponseStatusException.class,()->service.updateOwnContact(null,new EmployeeContactPatchDTO()));
  verifyNoInteractions(repository,employees,audit);
 }
}
