package com.hrm.hrm.service;

import java.security.Principal;
import java.util.List;
import com.hrm.controller.*;
import com.hrm.exception.GlobalExceptionHandler;
import com.hrm.service.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.*;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.server.ResponseStatusException;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class G1EndpointAccessTest {
    @Mock DepartmentService departments;
    @Mock PositionService positions;
    @Mock CatalogAuditService catalogs;
    @Mock EmployeeAssignmentService assignments;
    @Mock EmployeeTransferAuditService transfers;
    @Mock DepartmentHeadAccessService access;
    @Mock JdbcTemplate jdbc;
    MockMvc mvc;
    Principal employee=()->"employee-test";

    @BeforeEach void setup() {
        mvc=MockMvcBuilders.standaloneSetup(new DepartmentController(departments,access,catalogs),
                new PositionController(positions,access,catalogs),new EmployeeAssignmentController(assignments,access,transfers),
                new G1AuditController(jdbc,access)).setControllerAdvice(new GlobalExceptionHandler()).build();
    }

    private List<MockHttpServletRequestBuilder> requests() {
        String d="{\"departmentCode\":\"D1\",\"departmentName\":\"Phòng\"}";
        String p="{\"positionCode\":\"P1\",\"positionName\":\"Chức vụ\"}";
        String t="{\"departmentId\":1,\"positionId\":1,\"effectiveFrom\":\"2020-01-01\"}";
        return List.of(get("/departments").param("page","0"),get("/departments/1"),post("/departments").contentType(MediaType.APPLICATION_JSON).content(d),
                put("/departments/1").contentType(MediaType.APPLICATION_JSON).content(d),delete("/departments/1"),
                get("/positions").param("page","0"),get("/positions/1"),post("/positions").contentType(MediaType.APPLICATION_JSON).content(p),
                put("/positions/1").contentType(MediaType.APPLICATION_JSON).content(p),delete("/positions/1"),
                get("/employee-assignments"),get("/employees/1/assignments"),
                put("/employees/1/transfer").contentType(MediaType.APPLICATION_JSON).content(t),
                get("/g1/audit").param("targetTable","employees").param("recordId","1"));
    }

    @Test void anonymousCannotReadOrWriteAnyG1CatalogOrAssignment() throws Exception {
        when(access.requireManager(null)).thenThrow(new ResponseStatusException(HttpStatus.UNAUTHORIZED));
        for(var request:requests()) mvc.perform(request).andExpect(status().isUnauthorized());
        verifyNoInteractions(departments,positions,catalogs,assignments,transfers,jdbc);
    }

    @Test void forbiddenEmployeeCannotReadOrWriteAnyG1CatalogOrAssignment() throws Exception {
        when(access.requireManager(employee)).thenThrow(new ResponseStatusException(HttpStatus.FORBIDDEN));
        for(var request:requests()) mvc.perform(request.principal(employee)).andExpect(status().isForbidden());
        verifyNoInteractions(departments,positions,catalogs,assignments,transfers,jdbc);
    }
}
