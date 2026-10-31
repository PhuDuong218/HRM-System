package com.hrm.security;

import com.hrm.controller.PayrollBatchController;
import com.hrm.controller.PayrollPeriodController;
import com.hrm.dto.AdjustmentBatchRequestDTO;
import com.hrm.dto.CreateDraftBatchRequestDTO;
import com.hrm.dto.PayrollPeriodRequestDTO;
import com.hrm.repository.PayrollDetailRepository;
import com.hrm.repository.PayrollLineRepository;
import com.hrm.service.PayrollBatchService;
import com.hrm.service.PayrollExecutionService;
import com.hrm.service.PayrollPeriodService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Arrays;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PayrollManagementAuthorizationTest {
    @Configuration
    @EnableMethodSecurity
    static class MethodSecurity {}

    AnnotationConfigApplicationContext context;
    PayrollBatchService service;
    PayrollPeriodService periods;
    PayrollDetailRepository details;
    PayrollLineRepository lines;
    PayrollExecutionService execution;
    PayrollBatchController batches;
    PayrollPeriodController periodController;

    @BeforeEach void setUp() {
        service=mock(PayrollBatchService.class); periods=mock(PayrollPeriodService.class);
        details=mock(PayrollDetailRepository.class); lines=mock(PayrollLineRepository.class);
        execution=mock(PayrollExecutionService.class);
        context=new AnnotationConfigApplicationContext();
        context.register(MethodSecurity.class);
        context.registerBean(PayrollBatchController.class,()->new PayrollBatchController(service,execution,details,lines));
        context.registerBean(PayrollPeriodController.class,()->new PayrollPeriodController(periods));
        context.refresh();
        batches=context.getBean(PayrollBatchController.class);
        periodController=context.getBean(PayrollPeriodController.class);
    }
    @AfterEach void cleanUp() { SecurityContextHolder.clearContext(); context.close(); }
    void login(String role,String... permissions) {
        var authorities=new java.util.ArrayList<SimpleGrantedAuthority>();
        authorities.add(new SimpleGrantedAuthority("ROLE_"+role));
        Arrays.stream(permissions).map(SimpleGrantedAuthority::new).forEach(authorities::add);
        var actor=new AuthenticatedAccount(71L,81L,"qa-payroll",role);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(actor,null,authorities));
    }
    @ParameterizedTest @ValueSource(strings={"ADMIN","HR"})
    void managersWithPermissionCanReadAndWrite(String role) {
        login(role,"PAYROLL_MANAGE");
        assertDoesNotThrow(()->batches.getAll());
        assertDoesNotThrow(()->batches.createDraft(new CreateDraftBatchRequestDTO()));
        assertDoesNotThrow(()->periodController.create(new PayrollPeriodRequestDTO()));
        verify(service).createDraftBatch(any()); verify(periods).createPeriod(any());
    }
    @ParameterizedTest @ValueSource(strings={"ADMIN","HR"})
    void roleAloneDoesNotGrantPayrollAccess(String role) {
        login(role);
        assertThrows(AccessDeniedException.class,()->batches.getAll());
        assertThrows(AccessDeniedException.class,()->batches.createDraft(new CreateDraftBatchRequestDTO()));
        assertThrows(AccessDeniedException.class,()->periodController.getAll());
        verifyNoInteractions(service,periods,details,lines,execution);
    }
    @Test void employeeCannotEscalateEvenWithPayrollAuthorities() {
        login("EMPLOYEE","PAYROLL_MANAGE","PAYROLL_VIEW");
        assertThrows(AccessDeniedException.class,()->batches.getAll());
        assertThrows(AccessDeniedException.class,()->batches.getByPeriod(1L));
        assertThrows(AccessDeniedException.class,()->batches.getBatchDetails(1L));
        assertThrows(AccessDeniedException.class,()->batches.createDraft(new CreateDraftBatchRequestDTO()));
        assertThrows(AccessDeniedException.class,()->batches.calculatePayroll(1L));
        assertThrows(AccessDeniedException.class,()->batches.lockBatch(1L,999L));
        assertThrows(AccessDeniedException.class,()->batches.publishBatch(1L,999L));
        assertThrows(AccessDeniedException.class,()->batches.createAdjustment(1L,new AdjustmentBatchRequestDTO()));
        assertThrows(AccessDeniedException.class,()->periodController.getAll());
        assertThrows(AccessDeniedException.class,()->periodController.getById(1L));
        assertThrows(AccessDeniedException.class,()->periodController.create(new PayrollPeriodRequestDTO()));
        assertThrows(AccessDeniedException.class,()->periodController.update(1L,new PayrollPeriodRequestDTO()));
        verifyNoInteractions(service,periods,details,lines,execution);
    }
    @Test void viewPermissionDoesNotAllowPayrollMutation() {
        login("ADMIN","PAYROLL_VIEW");
        assertDoesNotThrow(()->batches.getAll());
        assertDoesNotThrow(()->periodController.getAll());
        assertThrows(AccessDeniedException.class,()->batches.createDraft(new CreateDraftBatchRequestDTO()));
        assertThrows(AccessDeniedException.class,()->periodController.create(new PayrollPeriodRequestDTO()));
        verify(service,never()).createDraftBatch(any()); verify(periods,never()).createPeriod(any());
    }
    @Test void lockAndPublishIgnoreForgedActorQueryParameter() {
        login("ADMIN","PAYROLL_MANAGE");
        batches.lockBatch(1L,999L); batches.publishBatch(1L,999L);
        verify(service).lockBatch(1L,71L); verify(service).publishBatch(1L,71L);
        verify(service,never()).lockBatch(1L,999L); verify(service,never()).publishBatch(1L,999L);
    }
}
