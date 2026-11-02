package com.hrm.service;

import com.hrm.dto.WorkTimeDTOs.ScheduleView;
import com.hrm.security.AuthenticatedAccount;
import java.sql.ResultSet;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.web.server.ResponseStatusException;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ScheduleChangeAuthorizationRegressionTest {
    private final JdbcTemplate db=mock(JdbcTemplate.class);
    private final DetailedWorkTimeService detailed=mock(DetailedWorkTimeService.class);
    private final ScheduleWritePermissionService permission=mock(ScheduleWritePermissionService.class);
    private final ScheduleChangeRequestService service=new ScheduleChangeRequestService(db,new ScheduleChangePolicy(),detailed,permission);
    private final AuthenticatedAccount hr=new AuthenticatedAccount(20L,5L,"hr","HR");
    private final LocalDateTime start=LocalDateTime.now().plusDays(20);

    @SuppressWarnings({"rawtypes","unchecked"})
    private void actor() throws Exception {
        when(db.query(contains("SELECT a.account_id,a.employee_id,r.role_code"),any(RowMapper.class),eq(20L))).thenAnswer(call->{
            ResultSet rs=mock(ResultSet.class);when(rs.getLong(1)).thenReturn(20L);when(rs.getObject(2,Long.class)).thenReturn(5L);when(rs.getString(3)).thenReturn("HR");
            return List.of(((RowMapper)call.getArgument(1)).mapRow(rs,0));
        });
    }
    @SuppressWarnings("unchecked")
    private void request(long employee,String status) {
        when(db.query(contains("SELECT r.employee_id,r.status,r.submitted_at"),any(RowMapper.class),eq(10L)))
          .thenReturn(Collections.singletonList(new Object[]{employee,status,90L,start,start.plusHours(4),1L,start.plusDays(1),start.plusDays(1).plusHours(4),new byte[8]}));
    }
    @Test void hrCannotApproveOwnRequest() throws Exception {
        actor();request(5,"CHO_DUYET");
        var error=assertThrows(ResponseStatusException.class,()->service.decide(hr,10,true,null));
        assertEquals(HttpStatus.FORBIDDEN,error.getStatusCode());verifyNoInteractions(detailed);
    }
    @Test void alreadyProcessedRequestCannotBeApprovedAgain() throws Exception {
        actor();request(9,"DA_DUYET");
        assertThrows(ResponseStatusException.class,()->service.decide(hr,10,true,null));verifyNoInteractions(detailed);
    }
    @SuppressWarnings("unchecked")
    @Test void rejectionAlsoRequiresDepartmentScope() throws Exception {
        actor();request(9,"CHO_DUYET");
        var old=new ScheduleView(90,9,1L,start.toLocalDate(),start,start.plusHours(4),0,"");
        when(db.query(contains("SELECT schedule_id,employee_id,shift_id,work_date,planned_start,planned_end,planned_break_minutes,note,row_version"),any(RowMapper.class),eq(90L)))
                .thenReturn(Collections.singletonList(new Object[]{old,new byte[8]}));
        doThrow(new ResponseStatusException(HttpStatus.FORBIDDEN)).when(permission).requireUpdate(eq(hr),eq(old),any());
        assertThrows(ResponseStatusException.class,()->service.decide(hr,10,false,"Không phù hợp"));
        verifyNoInteractions(detailed);
        verify(db,never()).update(contains("UPDATE dbo.employee_requests"),any(Object[].class));
    }
}
