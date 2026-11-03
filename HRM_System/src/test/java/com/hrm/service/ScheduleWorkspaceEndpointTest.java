package com.hrm.service;

import com.hrm.controller.ScheduleWorkspaceController;
import com.hrm.dto.PageResponse;
import com.hrm.security.AuthenticatedAccount;
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

class ScheduleWorkspaceEndpointTest {
    private final ScheduleWorkspaceService workspace=mock(ScheduleWorkspaceService.class);
    private final ScheduleChangeRequestService changes=mock(ScheduleChangeRequestService.class);
    private final WorkflowRequestQueryService requests=mock(WorkflowRequestQueryService.class);
    private final AuthenticatedAccount actor=new AuthenticatedAccount(1L,1L,"admin","ADMIN");
    @AfterEach void clear(){SecurityContextHolder.clearContext();}
    @Test void listAndDecisionUseSelectedRequestAndTrustedActor() throws Exception {
        SecurityContextHolder.getContext().setAuthentication(UsernamePasswordAuthenticationToken.authenticated(actor,null,List.of()));
        when(requests.list(actor,"DOI_LICH",null,null,0,10)).thenReturn(new PageResponse<>(List.of(),0,10,0,0));
        var mvc=MockMvcBuilders.standaloneSetup(new ScheduleWorkspaceController(workspace,changes,requests,mock(JdbcTemplate.class)))
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver()).build();
        mvc.perform(get("/api/management/schedule-workspace/requests")).andExpect(status().isOk()).andExpect(jsonPath("$.items").isArray());
        mvc.perform(post("/api/management/schedule-workspace/requests/123/decision")
                .contentType(MediaType.APPLICATION_JSON).content("{\"approve\":false,\"rejectionReason\":\"Không phù hợp\"}"))
                .andExpect(status().isOk());
        verify(changes).decide(actor,123,false,"Không phù hợp");
        verify(workspace,times(2)).context(actor);
    }
}
