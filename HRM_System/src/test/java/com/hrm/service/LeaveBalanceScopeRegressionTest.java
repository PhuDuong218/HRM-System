package com.hrm.service;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import java.security.Principal;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class LeaveBalanceScopeRegressionTest {
 @Test void outsideScopeFailsBeforeReadingEntitlements(){var jdbc=mock(JdbcTemplate.class);var access=mock(PersonnelAccessService.class);var scope=mock(PersonnelScopeService.class);var service=new EmployeeRequestService(jdbc,access,mock(PersonnelWorkflowSupport.class),mock(AssignmentGuardService.class));ReflectionTestUtils.setField(service,"personnelScope",scope);Principal actor=()->"hr";doThrow(new ResponseStatusException(HttpStatus.FORBIDDEN)).when(scope).employee(12L,true);assertEquals(HttpStatus.FORBIDDEN,assertThrows(ResponseStatusException.class,()->service.balances(12L,actor)).getStatusCode());verify(access).ownerOrManager(actor,12L);verifyNoInteractions(jdbc);}
}
