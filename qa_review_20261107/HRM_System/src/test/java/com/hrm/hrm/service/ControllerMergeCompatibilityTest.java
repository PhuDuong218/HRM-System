package com.hrm.hrm.service;

import java.security.Principal;
import java.util.List;

import com.hrm.controller.DepartmentController;
import com.hrm.controller.EmployeeController;
import com.hrm.controller.PositionController;
import com.hrm.dto.DepartmentDTO;
import com.hrm.dto.DepartmentRequestDTO;
import com.hrm.dto.EmployeeDTO;
import com.hrm.dto.PositionDTO;
import com.hrm.exception.GlobalExceptionHandler;
import com.hrm.security.AuthenticatedAccount;
import com.hrm.service.CatalogAuditService;
import com.hrm.service.DepartmentHeadAccessService;
import com.hrm.service.DepartmentService;
import com.hrm.service.EmployeeCreationAuditService;
import com.hrm.service.EmployeeService;
import com.hrm.service.EmployeeTerminationAuditService;
import com.hrm.service.PositionService;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class ControllerMergeCompatibilityTest {

    @Mock DepartmentService departments;
    @Mock PositionService positions;
    @Mock EmployeeService employees;
    @Mock DepartmentHeadAccessService access;
    @Mock CatalogAuditService catalog;
    @Mock EmployeeCreationAuditService creation;
    @Mock EmployeeTerminationAuditService termination;

    private MockMvc mvc;
    private final Principal manager = () -> "hr-test";

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.standaloneSetup(
                new DepartmentController(departments, access, catalog),
                new PositionController(positions, access, catalog),
                new EmployeeController(employees, access, termination, creation))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void bareCollectionsRemainArraysAndExplicitPaginationReturnsPages() throws Exception {
        when(access.requireManager(manager)).thenReturn(7L);
        when(departments.getAllDepartment()).thenReturn(List.of(new DepartmentDTO()));
        when(positions.getAllPositions()).thenReturn(List.of(new PositionDTO()));
        when(employees.getAllEmployees()).thenReturn(List.of(new EmployeeDTO()));
        when(departments.searchDepartments(null, null, 0, 10))
                .thenReturn(Page.empty(PageRequest.of(0, 10)));
        when(positions.searchPositions(null, null, 0, 10))
                .thenReturn(Page.empty(PageRequest.of(0, 10)));
        when(employees.getEmployeesPage(0, 10)).thenReturn(Page.empty(PageRequest.of(0, 10)));

        for (String endpoint : List.of("/departments", "/positions", "/employees")) {
            mvc.perform(get(endpoint).principal(manager))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$").isArray())
                    .andExpect(jsonPath("$.length()").value(1));
            mvc.perform(get(endpoint).param("page", "0").principal(manager))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content").isArray())
                    .andExpect(jsonPath("$.number").value(0));
        }

        verify(departments).getAllDepartment();
        verify(positions).getAllPositions();
        verify(employees).getAllEmployees();
        verify(departments).searchDepartments(null, null, 0, 10);
        verify(positions).searchPositions(null, null, 0, 10);
        verify(employees).getEmployeesPage(0, 10);
    }

    @Test
    void catalogFiltersSelectPagedHandlersWithoutExplicitPagination() throws Exception {
        when(access.requireManager(manager)).thenReturn(7L);
        when(departments.searchDepartments("Engineering", true, 0, 10))
                .thenReturn(Page.empty(PageRequest.of(0, 10)));
        when(positions.searchPositions("Developer", null, 0, 10))
                .thenReturn(Page.empty(PageRequest.of(0, 10)));

        mvc.perform(get("/departments").param("keyword", "Engineering").param("active", "true")
                        .principal(manager))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray());
        mvc.perform(get("/positions").param("keyword", "Developer").principal(manager))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray());

        verify(departments).searchDepartments("Engineering", true, 0, 10);
        verify(positions).searchPositions("Developer", null, 0, 10);
    }

    @Test
    void searchRoutesAlwaysReturnPagesWithDefaultPagination() throws Exception {
        when(access.requireManager(manager)).thenReturn(7L);
        when(departments.searchDepartments(null, null, 0, 10))
                .thenReturn(Page.empty(PageRequest.of(0, 10)));
        when(positions.searchPositions(null, null, 0, 10))
                .thenReturn(Page.empty(PageRequest.of(0, 10)));
        when(employees.searchEmployees(null, null, null, null, 0, 10))
                .thenReturn(Page.empty(PageRequest.of(0, 10)));

        for (String endpoint : List.of("/departments/search", "/positions/search", "/employees/search")) {
            mvc.perform(get(endpoint).principal(manager))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content").isArray());
        }
    }

    @Test
    void jwtEmployeeReadsRetainActorScopeIncludingPersonalProfile() throws Exception {
        AuthenticatedAccount actor = new AuthenticatedAccount(7L, 100L, "hr-test", "HR");
        Authentication authentication = new UsernamePasswordAuthenticationToken(actor, null,
                List.of(new SimpleGrantedAuthority("ROLE_HR"), new SimpleGrantedAuthority("EMPLOYEE_MANAGE"),
                        new SimpleGrantedAuthority("SELF_READ")));
        when(employees.getAllEmployees(actor)).thenReturn(List.of(new EmployeeDTO()));
        when(employees.getEmployeeById(100L, actor)).thenReturn(new EmployeeDTO());

        mvc.perform(get("/employees").principal(authentication))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
        mvc.perform(get("/employees/100").principal(authentication)).andExpect(status().isOk());

        verify(employees).getAllEmployees(actor);
        verify(employees).getEmployeeById(100L, actor);
        verifyNoInteractions(access);
    }

    @Test
    void catalogAuditUsesAuthenticatedAccountIdWithoutRequiringEmployeeManage() throws Exception {
        AuthenticatedAccount actor = new AuthenticatedAccount(17L, 100L, "department-admin", "ADMIN");
        Authentication authentication = new UsernamePasswordAuthenticationToken(actor, null,
                List.of(new SimpleGrantedAuthority("ROLE_ADMIN"),
                        new SimpleGrantedAuthority("DEPARTMENT_MANAGE")));
        when(catalog.createDepartment(any(DepartmentRequestDTO.class), eq(17L)))
                .thenReturn(new DepartmentDTO());

        mvc.perform(post("/departments").principal(authentication)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"departmentCode\":\"D1\",\"departmentName\":\"Engineering\"}"))
                .andExpect(status().isCreated());

        verify(catalog).createDepartment(any(DepartmentRequestDTO.class), eq(17L));
        verifyNoInteractions(access);
    }
}
