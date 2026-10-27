package com.hrm.hrm.service;

import java.security.Principal;
import java.util.List;
import org.springframework.data.domain.PageRequest;
import com.hrm.controller.EmployeeController;
import com.hrm.dto.EmployeeDTO;
import com.hrm.dto.EmployeeRequestDTO;
import com.hrm.dto.EmployeeUpdateRequestDTO;
import com.hrm.enums.EmploymentStatus;
import com.hrm.exception.GlobalExceptionHandler;

import com.hrm.service.DepartmentHeadAccessService;
import com.hrm.service.EmployeeService;
import com.hrm.service.EmployeeTerminationAuditService;
import com.hrm.service.EmployeeCreationAuditService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.server.ResponseStatusException;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class EmployeeAccessControllerTest {

    @Mock
    private EmployeeService service;

    @Mock
    private DepartmentHeadAccessService access;

    @Mock
private EmployeeCreationAuditService creationAudit;
    @Mock
    private EmployeeTerminationAuditService terminationAudit;

    private MockMvc mvc;

    private final Principal manager = () -> "hr-test";

    private static final String CREATE_BODY = """
            {
                "employeeCode": "TEST-ACCESS-001",
                "fullName": "Nhân viên kiểm thử",
                "birthDate": "1990-01-01",
                "hireDate": "2020-01-01",
                "departmentId": 10,
                "positionId": 20
            }
            """;

    private static final String UPDATE_BODY = """
            {
                "fullName": "Nhân viên đã sửa",
                "birthDate": "1990-01-01"
            }
            """;

    @BeforeEach
    void prepare() {
        mvc = MockMvcBuilders.standaloneSetup(
                new EmployeeController(
        service, access, terminationAudit, creationAudit))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    private List<MockHttpServletRequestBuilder> requests() {
        return List.of(
                get("/employees"),

                get("/employees/100"),

                get("/employees/search")
                        .param("keyword", "An"),

                post("/employees")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(CREATE_BODY),

                put("/employees/100")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(UPDATE_BODY)
        );
    }

    @Test
    void anonymousUserCannotAccessAnyOfTheFiveEndpoints()
            throws Exception {

        when(access.requireManager(null))
                .thenThrow(new ResponseStatusException(
                        HttpStatus.UNAUTHORIZED));

        for (MockHttpServletRequestBuilder request : requests()) {
            mvc.perform(request)
                    .andExpect(status().isUnauthorized());
        }

        verify(access, times(5)).requireManager(null);

verifyNoInteractions(service, terminationAudit, creationAudit);    }

    @Test
    void forbiddenUserCannotAccessAnyOfTheFiveEndpoints()
            throws Exception {

        when(access.requireManager(manager))
                .thenThrow(new ResponseStatusException(
                        HttpStatus.FORBIDDEN));

        for (MockHttpServletRequestBuilder request : requests()) {
            mvc.perform(request.principal(manager))
                    .andExpect(status().isForbidden());
        }

        verify(access, times(5)).requireManager(manager);

        verifyNoInteractions(service, terminationAudit, creationAudit);
    }

    @Test
    void authorizedManagerCanUseAllFiveEndpoints()
            throws Exception {

        when(access.requireManager(manager)).thenReturn(7L);

        when(service.getEmployeesPage(0, 10))
        .thenReturn(
                Page.<EmployeeDTO>empty(PageRequest.of(0, 10)));

        when(service.getEmployeeById(100L))
                .thenReturn(new EmployeeDTO());

        when(service.searchEmployees(
        "An",
        EmploymentStatus.DANG_LAM,
        10L,
        20L,
        0,
        10))
        .thenReturn(
                Page.<EmployeeDTO>empty(PageRequest.of(0, 10)));

        when(creationAudit.create(
        any(EmployeeRequestDTO.class), eq(7L)))
        .thenReturn(new EmployeeDTO());

        when(creationAudit.update(
                eq(100L), any(EmployeeUpdateRequestDTO.class), eq(7L)))
                .thenReturn(new EmployeeDTO());

        mvc.perform(get("/employees").principal(manager))
                .andExpect(status().isOk());

        mvc.perform(get("/employees/100").principal(manager))
                .andExpect(status().isOk());

        mvc.perform(get("/employees/search")
                        .principal(manager)
                        .param("keyword", "An")
                        .param("status", "DANG_LAM")
                        .param("departmentId", "10")
                        .param("positionId", "20"))
                .andExpect(status().isOk());

        mvc.perform(post("/employees")
                        .principal(manager)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(CREATE_BODY))
                .andExpect(status().isCreated());

        mvc.perform(put("/employees/100")
                        .principal(manager)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(UPDATE_BODY))
                .andExpect(status().isOk());

        verify(access, times(5)).requireManager(manager);

        verify(service).getEmployeesPage(0, 10);
        verify(service).getEmployeeById(100L);

        verify(service).searchEmployees(
                "An",
                EmploymentStatus.DANG_LAM,
                10L,
                20L,
                0,
                10);

verify(creationAudit).create(
        any(EmployeeRequestDTO.class), eq(7L));
        verify(creationAudit).update(
                eq(100L), any(EmployeeUpdateRequestDTO.class), eq(7L));

        verifyNoInteractions(terminationAudit);
    }
}
