package com.hrm.service;

import com.hrm.exception.ResourceNotFoundException;
import java.security.Principal;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class PersonnelRequestIsolationTest {
    @Test void approvalRejectsMissingEvidenceUnderCurrentPolicyBeforeChargingBalance() {
        var db = mock(JdbcTemplate.class);
        var access = mock(PersonnelAccessService.class);
        var rules = mock(LeaveRequestRules.class);
        var service = new EmployeeRequestService(db, access, mock(PersonnelWorkflowSupport.class),
                mock(AssignmentGuardService.class));
        ReflectionTestUtils.setField(service, "personnelScope", mock(PersonnelScopeService.class));
        ReflectionTestUtils.setField(service, "leaveRules", rules);
        Principal principal = () -> "hr";
        when(access.approver(principal)).thenReturn(new PersonnelAccessService.Actor(1, 100, "HR"));
        when(db.queryForList(anyString(), eq(9L))).thenReturn(List.of(Map.of(
                "employeeId", 12L, "status", "CHO_DUYET", "requestType", "NGHI_PHEP",
                "leaveTypeId", 2L, "leaveStart", "2030-01-20T08:00", "leaveEnd", "2030-01-20T12:00")));
        when(db.queryForList(anyString(), eq(String.class), eq(12L))).thenReturn(List.of("DANG_LAM"));
        doThrow(new ResponseStatusException(HttpStatus.BAD_REQUEST)).when(rules)
                .eligibility(12L, 2L, null, java.time.LocalDate.of(2030, 1, 20));

        var decision = new com.hrm.dto.EmployeeRequestDTOs.Decision(null, "Nghiệm thu");
        assertEquals(HttpStatus.BAD_REQUEST, assertThrows(ResponseStatusException.class,
                () -> service.approve(9, decision, principal)).getStatusCode());
        verify(rules).evidence(9L);
        verify(db, never()).update(anyString(), any(Object[].class));
    }
    @Test void hrOutsideScopeCannotReadApproveOrRejectAnExistingRequest() {
        var db = mock(JdbcTemplate.class);
        var access = mock(PersonnelAccessService.class);
        var scope = mock(PersonnelScopeService.class);
        var service = new EmployeeRequestService(db, access, mock(PersonnelWorkflowSupport.class),
                mock(AssignmentGuardService.class));
        ReflectionTestUtils.setField(service, "personnelScope", scope);
        Principal principal = () -> "hr";
        var actor = new PersonnelAccessService.Actor(1, 100, "HR");
        when(access.actor(principal)).thenReturn(actor);
        when(access.approver(principal)).thenReturn(actor);
        when(db.queryForList(anyString(), eq(9L))).thenReturn(List.of(Map.of(
                "employeeId", 12L, "status", "CHO_DUYET")));
        doThrow(new ResponseStatusException(HttpStatus.FORBIDDEN)).when(scope).managedEmployee(12L);

        assertEquals(HttpStatus.FORBIDDEN, assertThrows(ResponseStatusException.class,
                () -> service.detail(9, principal)).getStatusCode());
        assertEquals(HttpStatus.FORBIDDEN, assertThrows(ResponseStatusException.class,
                () -> service.approve(9, null, principal)).getStatusCode());
        assertEquals(HttpStatus.FORBIDDEN, assertThrows(ResponseStatusException.class,
                () -> service.reject(9, null, principal)).getStatusCode());
        verify(db, times(3)).queryForList(anyString(), eq(9L));
        verifyNoMoreInteractions(db);
    }

    @Test void personnelDetailOnlyResolvesPersonnelRequestTypes() {
        var db=mock(JdbcTemplate.class);
        var access=mock(PersonnelAccessService.class);
        var service=new EmployeeRequestService(db,access,mock(PersonnelWorkflowSupport.class),mock(AssignmentGuardService.class));
        Principal principal=()->"admin";
        assertThrows(ResourceNotFoundException.class,()->service.detail(123,principal));
        verify(db).queryForList(contains("r.request_type IN ('NGHI_PHEP','NGHI_VIEC')"),eq(123L));
        verify(access,never()).ownerOrManager(any(),anyLong());
    }
}
