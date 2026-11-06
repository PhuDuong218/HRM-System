package com.hrm.service;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;
import static org.junit.jupiter.api.Assertions.*;
class SingleAdminRolePolicyTest {
 @Test void rejectsAdditionalAdminCreationAndPromotion(){
  for(String previous:new String[]{null,"HR","EMPLOYEE"}){
   var error=assertThrows(ResponseStatusException.class,()->AdminAccessService.validateAdminRoleAssignment(previous,"ADMIN"));
   assertEquals(409,error.getStatusCode().value());
  }
 }
 @Test void allowsExistingAdminToRetainRole(){assertDoesNotThrow(()->AdminAccessService.validateAdminRoleAssignment("ADMIN","ADMIN"));}
 @Test void allowsMultipleHrAndEmployeeAssignments(){
  for(String previous:new String[]{null,"HR","EMPLOYEE","ADMIN"})
   for(String target:new String[]{"HR","EMPLOYEE"})assertDoesNotThrow(()->AdminAccessService.validateAdminRoleAssignment(previous,target));
 }
}
