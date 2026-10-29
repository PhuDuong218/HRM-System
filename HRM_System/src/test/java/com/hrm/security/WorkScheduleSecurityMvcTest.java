package com.hrm.security;

import com.hrm.controller.WorkScheduleController;
import com.hrm.dto.DetailedWorkTimeDTOs.ScheduleWithBreaks;
import com.hrm.dto.WorkTimeDTOs.ScheduleView;
import com.hrm.service.ScheduleQueryService;
import com.hrm.service.DetailedWorkTimeService;
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
    private DetailedWorkTimeService detailed;
    private ScheduleQueryService queries;
    private MockMvc mvc;

    private static final AuthenticatedAccount ADMIN = new AuthenticatedAccount(2L, 1L, "admin.sample", "ADMIN");
    private static final AuthenticatedAccount HR = new AuthenticatedAccount(5L, 2L, "hr.sample", "HR");
    private static final AuthenticatedAccount EMPLOYEE = new AuthenticatedAccount(1L, 3L, "employee.a", "EMPLOYEE");

    private static final String VALID_INPUT = """
            {"schedule":{"employeeId":3,"shiftId":null,"plannedStart":"2030-05-01T08:00:00",
             "plannedEnd":"2030-05-01T17:00:00","plannedBreakMinutes":60,"note":"mvc-test"},
             "breaks":[{"start":"2030-05-01T12:00:00","end":"2030-05-01T13:00:00"}]}
            """;

    private static ScheduleView view(long id) {
        return new ScheduleView(id, 3L, null, LocalDate.of(2030, 5, 1),
                LocalDateTime.of(2030, 5, 1, 8, 0),
                LocalDateTime.of(2030, 5, 1, 17, 0), 60, "mvc-test");
    }

    @BeforeEach
    void setUp() {
        schedules = mock(WorkScheduleService.class);
        detailed = mock(DetailedWorkTimeService.class);
        queries = mock(ScheduleQueryService.class);
        mvc = MockMvcBuilders.standaloneSetup(new WorkScheduleController(schedules, queries, detailed))
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
        when(detailed.createSchedule(eq(ADMIN), any(ScheduleWithBreaks.class))).thenReturn(view(101L));
        mvc.perform(post("/api/management/schedules")
                .contentType(MediaType.APPLICATION_JSON).content(VALID_INPUT))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.scheduleId").value(101));
        verify(detailed).createSchedule(eq(ADMIN), argThat(v -> v.schedule().employeeId().equals(3L)
                && v.schedule().plannedBreakMinutes() == 60 && v.breaks().size() == 1));
    }

    @Test
    void hrPostDelegatesCorrectPrincipal() throws Exception {
        authenticateAs(HR);
        when(detailed.createSchedule(eq(HR), any(ScheduleWithBreaks.class))).thenReturn(view(102L));
        mvc.perform(post("/api/management/schedules")
                .contentType(MediaType.APPLICATION_JSON).content(VALID_INPUT))
                .andExpect(status().isOk());
        verify(detailed).createSchedule(eq(HR), any(ScheduleWithBreaks.class));
    }

    @Test
    void adminPutDelegatesCorrectPrincipalAndScheduleId() throws Exception {
        authenticateAs(ADMIN);
        when(detailed.updateSchedule(eq(ADMIN), eq(44L), any(ScheduleWithBreaks.class))).thenReturn(view(44L));
        mvc.perform(put("/api/management/schedules/44")
                .contentType(MediaType.APPLICATION_JSON).content(VALID_INPUT))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.scheduleId").value(44));
        verify(detailed).updateSchedule(eq(ADMIN), eq(44L), any(ScheduleWithBreaks.class));
    }

    @Test
    void hrPutDelegatesCorrectPrincipalAndScheduleId() throws Exception {
        authenticateAs(HR);
        when(detailed.updateSchedule(eq(HR), eq(45L), any(ScheduleWithBreaks.class))).thenReturn(view(45L));
        mvc.perform(put("/api/management/schedules/45")
                .contentType(MediaType.APPLICATION_JSON).content(VALID_INPUT))
                .andExpect(status().isOk());
        verify(detailed).updateSchedule(eq(HR), eq(45L), any(ScheduleWithBreaks.class));
    }

    @Test
    void employeePostPropagatesServiceForbidden() throws Exception {
        authenticateAs(EMPLOYEE);
        when(detailed.createSchedule(eq(EMPLOYEE), any(ScheduleWithBreaks.class)))
                .thenThrow(new ResponseStatusException(HttpStatus.FORBIDDEN, "No schedule permission"));
        mvc.perform(post("/api/management/schedules")
                .contentType(MediaType.APPLICATION_JSON).content(VALID_INPUT))
                .andExpect(status().isForbidden());
        verify(detailed).createSchedule(eq(EMPLOYEE), any(ScheduleWithBreaks.class));
    }

    @Test
    void employeePutPropagatesServiceForbidden() throws Exception {
        authenticateAs(EMPLOYEE);
        when(detailed.updateSchedule(eq(EMPLOYEE), eq(45L), any(ScheduleWithBreaks.class)))
                .thenThrow(new ResponseStatusException(HttpStatus.FORBIDDEN, "No schedule permission"));
        mvc.perform(put("/api/management/schedules/45")
                .contentType(MediaType.APPLICATION_JSON).content(VALID_INPUT))
                .andExpect(status().isForbidden());
        verify(detailed).updateSchedule(eq(EMPLOYEE), eq(45L), any(ScheduleWithBreaks.class));
    }

    @Test
    void missingEmployeeIdReturns400WithoutInvokingService() throws Exception {
        authenticateAs(ADMIN);
        String invalid = """
                {"schedule":{"plannedStart":"2030-05-01T08:00:00",
                 "plannedEnd":"2030-05-01T17:00:00","plannedBreakMinutes":60},"breaks":[]}
                """;
        mvc.perform(post("/api/management/schedules")
                .contentType(MediaType.APPLICATION_JSON).content(invalid))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(schedules, detailed);
    }

    @Test
    void malformedJsonReturns400WithoutInvokingService() throws Exception {
        authenticateAs(ADMIN);
        mvc.perform(put("/api/management/schedules/45")
                .contentType(MediaType.APPLICATION_JSON).content("{not-json}"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(schedules, detailed);
    }
}
