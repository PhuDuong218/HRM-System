package com.hrm.security;

import com.hrm.controller.WorkScheduleController;
import com.hrm.dto.WorkTimeDTOs.ScheduleInput;
import com.hrm.dto.WorkTimeDTOs.ScheduleView;
import com.hrm.service.ScheduleQueryService;
import com.hrm.service.WorkScheduleService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Standalone MVC boundary tests. No Spring Boot application context, database,
 * HTTP server, real JWT parsing or SecurityFilterChain is started.
 *
 * These tests verify controller argument binding, validation, delegation and
 * propagation of 403 responses from the permission-protected service. They DO
 * NOT establish that an unauthenticated/invalid JWT is rejected; those cases
 * must be covered separately against the real SecurityFilterChain.
 */
class WorkScheduleSecurityMvcTest {
    private WorkScheduleService schedules;
    private ScheduleQueryService queries;
    private MockMvc mvc;

    private static final AuthenticatedAccount ADMIN = new AuthenticatedAccount(2L, 1L, "admin.sample", "ADMIN");
    private static final AuthenticatedAccount HR = new AuthenticatedAccount(5L, 2L, "hr.sample", "HR");
    private static final AuthenticatedAccount EMPLOYEE = new AuthenticatedAccount(1L, 3L, "employee.a", "EMPLOYEE");

    private static final String VALID_INPUT = """
            {"employeeId":3,"shiftId":null,"plannedStart":"2030-05-01T08:00:00",
             "plannedEnd":"2030-05-01T17:00:00","plannedBreakMinutes":60,"note":"mvc-test"}
            """;

    private static ScheduleView view(long id) {
        return new ScheduleView(id, 3L, null, LocalDate.of(2030, 5, 1),
                LocalDateTime.of(2030, 5, 1, 8, 0),
                LocalDateTime.of(2030, 5, 1, 17, 0), 60, "mvc-test");
    }

    @BeforeEach
    void setUp() {
        schedules = mock(WorkScheduleService.class);
        queries = mock(ScheduleQueryService.class);
        mvc = MockMvcBuilders.standaloneSetup(new WorkScheduleController(schedules, queries))
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
                .build();
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    private void authenticateAs(AuthenticatedAccount actor) {
        var auth = new UsernamePasswordAuthenticationToken(actor, null, List.of());
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    @Test
    void adminPostDelegatesAuthenticatedPrincipalAndValidatedBody() throws Exception {
        authenticateAs(ADMIN);
        when(schedules.create(eq(ADMIN), any(ScheduleInput.class))).thenReturn(view(101L));
        mvc.perform(post("/api/management/schedules")
                .contentType(MediaType.APPLICATION_JSON).content(VALID_INPUT))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.scheduleId").value(101));
        verify(schedules).create(eq(ADMIN), argThat(v -> v.employeeId().equals(3L)
                && v.plannedBreakMinutes() == 60));
    }

    @Test
    void hrPostDelegatesCorrectPrincipal() throws Exception {
        authenticateAs(HR);
        when(schedules.create(eq(HR), any(ScheduleInput.class))).thenReturn(view(102L));
        mvc.perform(post("/api/management/schedules")
                .contentType(MediaType.APPLICATION_JSON).content(VALID_INPUT))
                .andExpect(status().isOk());
        verify(schedules).create(eq(HR), any(ScheduleInput.class));
    }

    @Test
    void adminPutDelegatesCorrectPrincipalAndScheduleId() throws Exception {
        authenticateAs(ADMIN);
        when(schedules.update(eq(ADMIN), eq(44L), any(ScheduleInput.class))).thenReturn(view(44L));
        mvc.perform(put("/api/management/schedules/44")
                .contentType(MediaType.APPLICATION_JSON).content(VALID_INPUT))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.scheduleId").value(44));
        verify(schedules).update(eq(ADMIN), eq(44L), any(ScheduleInput.class));
    }

    @Test
    void hrPutDelegatesCorrectPrincipalAndScheduleId() throws Exception {
        authenticateAs(HR);
        when(schedules.update(eq(HR), eq(45L), any(ScheduleInput.class))).thenReturn(view(45L));
        mvc.perform(put("/api/management/schedules/45")
                .contentType(MediaType.APPLICATION_JSON).content(VALID_INPUT))
                .andExpect(status().isOk());
        verify(schedules).update(eq(HR), eq(45L), any(ScheduleInput.class));
    }

    @Test
    void employeePostPropagatesServiceForbidden() throws Exception {
        authenticateAs(EMPLOYEE);
        when(schedules.create(eq(EMPLOYEE), any(ScheduleInput.class)))
                .thenThrow(new ResponseStatusException(HttpStatus.FORBIDDEN, "No schedule permission"));
        mvc.perform(post("/api/management/schedules")
                .contentType(MediaType.APPLICATION_JSON).content(VALID_INPUT))
                .andExpect(status().isForbidden());
        verify(schedules).create(eq(EMPLOYEE), any(ScheduleInput.class));
    }

    @Test
    void employeePutPropagatesServiceForbidden() throws Exception {
        authenticateAs(EMPLOYEE);
        when(schedules.update(eq(EMPLOYEE), eq(45L), any(ScheduleInput.class)))
                .thenThrow(new ResponseStatusException(HttpStatus.FORBIDDEN, "No schedule permission"));
        mvc.perform(put("/api/management/schedules/45")
                .contentType(MediaType.APPLICATION_JSON).content(VALID_INPUT))
                .andExpect(status().isForbidden());
        verify(schedules).update(eq(EMPLOYEE), eq(45L), any(ScheduleInput.class));
    }

    @Test
    void missingEmployeeIdReturns400WithoutInvokingService() throws Exception {
        authenticateAs(ADMIN);
        String invalid = """
                {"plannedStart":"2030-05-01T08:00:00",
                 "plannedEnd":"2030-05-01T17:00:00","plannedBreakMinutes":60}
                """;
        mvc.perform(post("/api/management/schedules")
                .contentType(MediaType.APPLICATION_JSON).content(invalid))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(schedules);
    }

    @Test
    void malformedJsonReturns400WithoutInvokingService() throws Exception {
        authenticateAs(ADMIN);
        mvc.perform(put("/api/management/schedules/45")
                .contentType(MediaType.APPLICATION_JSON).content("{not-json}"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(schedules);
    }
}
