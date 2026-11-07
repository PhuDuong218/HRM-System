package com.hrm.controller;

import com.hrm.dto.AttendanceDTOs.AttendanceCorrection;
import com.hrm.dto.AttendanceDTOs.AttendanceInput;
import com.hrm.dto.AttendanceDTOs.AttendanceView;
import com.hrm.security.AuthenticatedAccount;
import com.hrm.service.AttendanceService;
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
 * Standalone MockMvc tests for the attendance Controller HTTP boundary.
 * NO actual JWT verification, SecurityFilterChain, Spring Boot application
 * context or SQL Server is involved. Authorization is enforced by the mocked
 * service only when explicitly stubbed to reject a call.
 */
class AttendanceControllerSecurityMockMvcTest {

    private static final AuthenticatedAccount ADMIN = new AuthenticatedAccount(20L, null, "it06.admin", "ADMIN");
    private static final AuthenticatedAccount HR = new AuthenticatedAccount(21L, 2L, "it06.hr", "HR");
    private static final AuthenticatedAccount EMPLOYEE = new AuthenticatedAccount(22L, 3L, "it06.employee", "EMPLOYEE");

    private static final String MANUAL_JSON = """
            {"employeeId":3,"workDate":"2030-05-01",
             "actualStart":"2030-05-01T08:00:00",
             "actualEnd":"2030-05-01T17:00:00",
             "actualBreakMinutes":60,"attendanceResult":"DI_LAM"}
            """;

    private static final String CORRECTION_JSON = """
            {"actualStart":"2030-05-01T08:15:00",
             "actualEnd":"2030-05-01T17:00:00",
             "actualBreakMinutes":60,"attendanceResult":"DI_LAM",
             "correctionReason":"Điều chỉnh giờ vào"}
            """;

    private AttendanceService service;
    private MockMvc mvc;

    @BeforeEach
    void setup() {
        service = mock(AttendanceService.class);
        mvc = MockMvcBuilders.standaloneSetup(new AttendanceController(service))
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
                .build();
    }

    @AfterEach
    void cleanup() {
        SecurityContextHolder.clearContext();
    }

    private static void authenticate(AuthenticatedAccount account) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(account, null, List.of()));
    }

    private static AttendanceView view(long id) {
        return new AttendanceView(id, 3L, null, LocalDate.of(2030, 5, 1),
                LocalDateTime.of(2030, 5, 1, 8, 0),
                LocalDateTime.of(2030, 5, 1, 17, 0), 60,
                "DI_LAM", "CHUA_XAC_NHAN", null, null, null);
    }

    @Test
    void manualUsesTrustedPrincipalNotClientActorId() throws Exception {
        authenticate(ADMIN);
        when(service.createManual(any(AttendanceInput.class), eq(ADMIN.accountId())))
                .thenReturn(view(101));
        mvc.perform(post("/api/attendance/manual")
                .contentType(MediaType.APPLICATION_JSON)
                .content(MANUAL_JSON.replace("\"employeeId\"", "\"actorAccountId\":999,\"employeeId\"")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.attendanceId").value(101));
        verify(service).createManual(argThat(v -> v.employeeId().equals(3L)
                && v.actualBreakMinutes() == 60), eq(ADMIN.accountId()));
        verifyNoMoreInteractions(service);
    }

    @Test
    void correctDelegatesPathIdAndAuthenticatedActor() throws Exception {
        authenticate(HR);
        when(service.correct(eq(44L), any(AttendanceCorrection.class), eq(HR.accountId())))
                .thenReturn(view(44));
        mvc.perform(put("/api/attendance/44/correct")
                .contentType(MediaType.APPLICATION_JSON).content(CORRECTION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.attendanceId").value(44));
        verify(service).correct(eq(44L), argThat(v -> v.correctionReason().equals("Điều chỉnh giờ vào")),
                eq(HR.accountId()));
    }

    @Test
    void confirmDelegatesAuthenticatedActor() throws Exception {
        authenticate(HR);
        when(service.confirm(45L, HR.accountId())).thenReturn(view(45));
        mvc.perform(post("/api/attendance/45/confirm"))
                .andExpect(status().isOk());
        verify(service).confirm(45L, HR.accountId());
    }

    @Test
    void unconfirmDelegatesReasonAndAuthenticatedActor() throws Exception {
        authenticate(HR);
        when(service.unconfirm(46L, "Kiểm tra lại", HR.accountId())).thenReturn(view(46));
        mvc.perform(post("/api/attendance/46/unconfirm")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\":\"Kiểm tra lại\",\"actorAccountId\":999}"))
                .andExpect(status().isOk());
        verify(service).unconfirm(46L, "Kiểm tra lại", HR.accountId());
    }

    @Test
    void missingAuthenticatedPrincipalReturns401BeforeService() throws Exception {
        mvc.perform(post("/api/attendance/45/confirm"))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(service);
    }

    @Test
    void employeeForbiddenIsPropagatedFromService() throws Exception {
        authenticate(EMPLOYEE);
        when(service.createManual(any(AttendanceInput.class), eq(EMPLOYEE.accountId())))
                .thenThrow(new ResponseStatusException(HttpStatus.FORBIDDEN, "No attendance permission"));
        mvc.perform(post("/api/attendance/manual")
                .contentType(MediaType.APPLICATION_JSON).content(MANUAL_JSON))
                .andExpect(status().isForbidden());
        verify(service).createManual(any(AttendanceInput.class), eq(EMPLOYEE.accountId()));
    }

    @Test
    void hrSeparationOfDutiesForbiddenIsPropagated() throws Exception {
        authenticate(HR);
        when(service.confirm(47L, HR.accountId()))
                .thenThrow(new ResponseStatusException(HttpStatus.FORBIDDEN, "Cannot self-confirm"));
        mvc.perform(post("/api/attendance/47/confirm"))
                .andExpect(status().isForbidden());
        verify(service).confirm(47L, HR.accountId());
    }

    @Test
    void hrSelfUnconfirmForbiddenIsPropagated() throws Exception {
        authenticate(HR);
        when(service.unconfirm(48L, "Sửa", HR.accountId()))
                .thenThrow(new ResponseStatusException(HttpStatus.FORBIDDEN, "Cannot self-unconfirm"));
        mvc.perform(post("/api/attendance/48/unconfirm")
                .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"Sửa\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void alreadyUnconfirmedConflictIsPropagated() throws Exception {
        authenticate(HR);
        when(service.unconfirm(48L, "Sửa", HR.accountId()))
                .thenThrow(new ResponseStatusException(HttpStatus.CONFLICT, "Already unconfirmed"));
        mvc.perform(post("/api/attendance/48/unconfirm")
                .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"Sửa\"}"))
                .andExpect(status().isConflict());
    }

    @Test
    void blankReasonReturns400WithoutServiceCall() throws Exception {
        authenticate(HR);
        mvc.perform(post("/api/attendance/48/unconfirm")
                .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"   \"}"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }

    @Test
    void invalidManualPayloadReturns400WithoutServiceCall() throws Exception {
        authenticate(HR);
        mvc.perform(post("/api/attendance/manual")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"employeeId\":3,\"attendanceResult\":\"DI_LAM\"}"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }

    @Test
    void malformedJsonReturns400WithoutServiceCall() throws Exception {
        authenticate(HR);
        mvc.perform(put("/api/attendance/48/correct")
                .contentType(MediaType.APPLICATION_JSON).content("{invalid"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }
}
