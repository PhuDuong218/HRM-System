package com.hrm.service;

import com.hrm.controller.G1AuditController;
import com.hrm.controller.PersonnelAuditController;
import com.hrm.security.AuthenticatedAccount;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.server.ResponseStatusException;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AuditReadPolicyTest {
    @AfterEach void clearIdentity() { SecurityContextHolder.clearContext(); }

    @Test void g1RejectsOutOfScopeBeforeReadingSnapshots() {
        var scope = mock(PersonnelScopeService.class);
        var db = mock(JdbcTemplate.class);
        doThrow(new ResponseStatusException(HttpStatus.FORBIDDEN)).when(scope).employee(12L,true);
        var controller = new G1AuditController(db,mock(DepartmentHeadAccessService.class),new AuditReadPolicy(scope));
        assertEquals(HttpStatus.FORBIDDEN,assertThrows(ResponseStatusException.class,
                ()->controller.history("employees",12L,0,10,()->"hr")).getStatusCode());
        verifyNoInteractions(db);
    }

    @Test void g2ResolvesEmployeeScopeBeforeReadingSnapshots() {
        var scope = mock(PersonnelScopeService.class);
        var db = mock(JdbcTemplate.class);
        when(scope.employeeOf("employee_qualifications","employee_qualification_id",9L)).thenReturn(12L);
        doThrow(new ResponseStatusException(HttpStatus.FORBIDDEN)).when(scope).employee(12L,true);
        var controller = new PersonnelAuditController(db,mock(PersonnelAccessService.class),new AuditReadPolicy(scope));
        assertEquals(HttpStatus.FORBIDDEN,assertThrows(ResponseStatusException.class,
                ()->controller.history("employee_qualifications",9L,0,10,()->"hr")).getStatusCode());
        verifyNoInteractions(db);
    }

    @Test void nonOwnerCannotReadPrivateFieldsEvenInsideNestedSnapshots() {
        var policy=new AuditReadPolicy(mock(PersonnelScopeService.class));
        String result=policy.snapshot("employees","12","{\"fullName\":\"Employee\",\"identityNumber\":\"private-id\",\"nested\":[{\"home_address\":\"private-home\",\"position\":\"HR\"}]}");
        assertFalse(result.contains("private-id"));assertFalse(result.contains("private-home"));
        assertTrue(result.contains("Employee"));assertTrue(result.contains("HR"));
    }

    @Test void privateSnapshotsFollowTheSameEmployeeReadScope() {
        var scope=mock(PersonnelScopeService.class);
        when(scope.canReadPrivate(12L)).thenReturn(true);
        var policy=new AuditReadPolicy(scope);
        assertTrue(policy.snapshot("employees","12","{\"identityNumber\":\"scoped-id\"}").contains("scoped-id"));
        assertFalse(policy.snapshot("employees","13","{\"identityNumber\":\"outside-id\"}").contains("outside-id"));
    }

    @Test void ownerCanReadOwnSnapshotButMalformedOtherSnapshotFailsClosed() {
        var actor=new AuthenticatedAccount(1L,12L,"owner","HR");
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(actor,"",List.of()));
        var policy=new AuditReadPolicy(mock(PersonnelScopeService.class));
        assertTrue(policy.snapshot("employees","12","{\"address\":\"own-home\"}").contains("own-home"));
        assertFalse(policy.snapshot("employees","13","invalid private-home").contains("private-home"));
    }
}
