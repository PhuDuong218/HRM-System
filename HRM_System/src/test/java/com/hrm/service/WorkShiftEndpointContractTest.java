package com.hrm.service;

import com.hrm.controller.WorkShiftController;
import com.hrm.controller.WorkShiftManagementWriteController;
import com.hrm.controller.WorkShiftReadOnlyController;
import com.hrm.dto.DetailedWorkTimeDTOs.OffsetBreak;
import com.hrm.dto.WorkTimeDTOs.ShiftView;
import com.hrm.security.AuthenticatedAccount;
import java.time.LocalTime;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class WorkShiftEndpointContractTest {
    private final AuthenticatedAccount actor = new AuthenticatedAccount(1L, 1L, "admin", "ADMIN");
    private final WorkShiftService shifts = mock(WorkShiftService.class);
    private final DetailedWorkTimeService detailed = mock(DetailedWorkTimeService.class);
    private final WorkShiftManagementWriteService management = mock(WorkShiftManagementWriteService.class);
    private final WorkShiftPermissionService permissions = mock(WorkShiftPermissionService.class);
    private final ShiftView view = new ShiftView(9, "DAY", "Ca ngày", LocalTime.of(8, 0), LocalTime.of(17, 0), 0, 60, true);

    @AfterEach void clear() { SecurityContextHolder.clearContext(); }

    @Test void taskEnabledExposesDetailsCreateAndReasonedLock() throws Exception {
        check(new WorkShiftController(shifts, detailed, management, permissions));
    }

    @Test void taskDisabledExposesSameFrontendContract() throws Exception {
        check(new WorkShiftReadOnlyController(shifts, permissions, mock(JdbcTemplate.class), detailed),
                new WorkShiftManagementWriteController(management, permissions));
    }

    private void check(Object... controllers) throws Exception {
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated(actor, null, List.of()));
        when(shifts.get(9)).thenReturn(view);
        when(detailed.shiftBreaks(9)).thenReturn(List.of(new OffsetBreak(240, 300)));
        when(management.create(eq(actor), any())).thenReturn(view);
        when(management.setActive(actor, 9, false, "Không dùng nữa")).thenReturn(view);
        var mvc = MockMvcBuilders.standaloneSetup(controllers)
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver()).build();
        mvc.perform(get("/api/management/shifts/9/details"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.shift.shiftCode").value("DAY"))
                .andExpect(jsonPath("$.breaks[0].startMinute").value(240));
        mvc.perform(post("/api/management/shifts").contentType(MediaType.APPLICATION_JSON).content("""
                {"shift":{"shiftCode":"DAY","shiftName":"Ca ngày","startTime":"08:00:00","endTime":"17:00:00","endDayOffset":0,"breakMinutes":60,"active":true},"breaks":[{"startMinute":240,"endMinute":300}]}
                """))
                .andExpect(status().isOk());
        mvc.perform(patch("/api/management/shifts/9/deactivate").contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\":\"Không dùng nữa\"}"))
                .andExpect(status().isOk());
        verify(management).setActive(actor, 9, false, "Không dùng nữa");
        mvc.perform(patch("/api/management/shifts/9/deactivate").contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\":\"\"}"))
                .andExpect(status().isBadRequest());
    }
}
