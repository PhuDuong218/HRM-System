package com.hrm;
import com.hrm.security.AuthenticatedAccount;
import com.hrm.service.OvertimePermissionService;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.server.ResponseStatusException;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class OvertimeP1PermissionTest {
 private final JdbcTemplate db=mock(JdbcTemplate.class);
 private final OvertimePermissionService permissions=spy(new OvertimePermissionService(db));
 private final AuthenticatedAccount admin=new AuthenticatedAccount(1L,10L,"admin","ADMIN");
 private void setup(String role,long admins){
  var actor=new OvertimePermissionService.VerifiedActor(1L,10L,"ADMIN");
  doReturn(actor).when(permissions).active(admin);
  doNothing().when(permissions).requirePermission(actor,"OVERTIME_APPROVE");
  doReturn(role).when(permissions).roleOfEmployee(20L);
  when(db.queryForObject(contains("r.role_code='ADMIN'"),eq(Long.class))).thenReturn(admins);
 }
 @Test void soleAdminCanApproveAndSeparatelyConfirmEmployeeAndHr(){
  for(String role:new String[]{"EMPLOYEE","HR"}){setup(role,1);assertEquals(1L,permissions.requireApprove(admin,20,LocalDate.now()).accountId());assertEquals(1L,permissions.requireConfirm(admin,20,LocalDate.now(),1).accountId());}
 }
 @Test void multipleAdminsCannotConfirmOwnApprovalForEitherTarget(){for(String role:new String[]{"EMPLOYEE","HR"}){setup(role,2);assertThrows(ResponseStatusException.class,()->permissions.requireConfirm(admin,20,LocalDate.now(),1));}}
 @Test void differentAdminCanConfirmEitherTarget(){for(String role:new String[]{"EMPLOYEE","HR"}){setup(role,2);assertDoesNotThrow(()->permissions.requireConfirm(admin,20,LocalDate.now(),2));}}
 @Test void exceptionDoesNotAllowAdminTarget(){setup("ADMIN",1);assertThrows(ResponseStatusException.class,()->permissions.requireConfirm(admin,20,LocalDate.now(),1));}
 @Test void exceptionDoesNotAllowOwnEmployeeIdentity(){setup("EMPLOYEE",1);doReturn("EMPLOYEE").when(permissions).roleOfEmployee(10L);assertThrows(ResponseStatusException.class,()->permissions.requireConfirm(admin,10,LocalDate.now(),1));}
}
