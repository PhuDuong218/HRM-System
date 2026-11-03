package com.hrm.security;

import com.hrm.controller.PayslipController;
import com.hrm.dto.PayslipDetailDTO;
import com.hrm.service.PayslipService;
import com.hrm.service.PersonnelScopeService;
import java.util.List;
import org.junit.jupiter.api.*;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.server.ResponseStatusException;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PayslipScopeTest {
    PayslipService service=mock(PayslipService.class);
    PersonnelScopeService scope=mock(PersonnelScopeService.class);
    PayslipController controller=new PayslipController(service,scope);

    void login(String role, String... permissions) {
        var actor=new AuthenticatedAccount(1L,10L,"synthetic",role);
        var authorities=java.util.Arrays.stream(permissions).map(SimpleGrantedAuthority::new).toList();
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(actor,null,authorities));
    }
    @AfterEach void clear(){SecurityContextHolder.clearContext();}

    @Test void personalListUsesAuthenticatedEmployeeOnly() {
        login("EMPLOYEE");
        controller.getMyPayslips(null);
        verify(service).getMyPayslips(10L);
        assertThrows(AccessDeniedException.class,()->controller.getMyPayslips(20L));
        verify(service,never()).getMyPayslips(20L);
    }
    @Test void hrNeedsPayrollPermissionAndScopeToReadOtherEmployee() {
        var detail=new PayslipDetailDTO();detail.setEmployeeId(20L);
        when(service.getPayslipDetail(2L)).thenReturn(detail);
        login("HR");assertThrows(AccessDeniedException.class,()->controller.getPayslipDetail(2L));
        login("HR","PAYROLL_VIEW");
        doThrow(new AccessDeniedException("outside scope")).when(scope).managedEmployee(20L);
        assertThrows(AccessDeniedException.class,()->controller.getPayslipDetail(2L));
        assertThrows(AccessDeniedException.class,()->controller.viewPayslipHtml(2L));
        verify(service,never()).renderPayslipHtml(anyLong());
    }
    @Test void ownPayslipRemainsAccessible() {
        var detail=new PayslipDetailDTO();detail.setEmployeeId(10L);
        when(service.getPayslipDetail(2L)).thenReturn(detail);
        login("HR");assertSame(detail,controller.getPayslipDetail(2L).getBody());
        verifyNoInteractions(scope);
    }
    @Test void personalAccessDoesNotGrantManagementOfOwnUnassignedDepartment() {
        login("HR");
        var realScope=new PersonnelScopeService(mock(JdbcTemplate.class));
        assertDoesNotThrow(()->realScope.employee(10L,true));
        assertThrows(ResponseStatusException.class,()->realScope.managedEmployee(10L));
    }
}
