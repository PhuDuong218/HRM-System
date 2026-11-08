package com.hrm.service;

import com.hrm.controller.LeaveEntitlementController;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class LeaveEntitlementRouteScopeTest {
 @Test void hrCannotReadAnotherDepartmentsEntitlements(){
  var data=mock(LeaveEntitlementService.class);var scope=mock(PersonnelScopeService.class);
  var controller=new LeaveEntitlementController(data,mock(PersonnelAccessService.class),mock(PersonnelCatalogService.class));
  ReflectionTestUtils.setField(controller,"scope",scope);
  doThrow(new ResponseStatusException(HttpStatus.FORBIDDEN)).when(scope).employee(41L,true);
  assertEquals(403,assertThrows(ResponseStatusException.class,()->controller.getByEmployeeId(41L,()->"hr")).getStatusCode().value());
  verifyNoInteractions(data);
 }
 @Test void permittedEmployeeStillLoads(){
  var data=mock(LeaveEntitlementService.class);var scope=mock(PersonnelScopeService.class);
  var controller=new LeaveEntitlementController(data,mock(PersonnelAccessService.class),mock(PersonnelCatalogService.class));
  ReflectionTestUtils.setField(controller,"scope",scope);
  controller.getByEmployeeId(48L,()->"hr");verify(scope).employee(48L,true);verify(data).getByEmployeeId(48L);
 }
}
