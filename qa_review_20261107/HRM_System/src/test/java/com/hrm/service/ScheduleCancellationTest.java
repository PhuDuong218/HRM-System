package com.hrm.service;

import com.hrm.dto.WorkTimeDTOs.*;
import com.hrm.security.AuthenticatedAccount;
import java.time.*;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ScheduleCancellationTest {
    private final JdbcTemplate db=mock(JdbcTemplate.class);
    private final ScheduleStateService states=mock(ScheduleStateService.class);
    private final ScheduleWritePermissionService permission=mock(ScheduleWritePermissionService.class);
    private final WorkScheduleService schedules=mock(WorkScheduleService.class);
    private final AuthenticatedAccount actor=new AuthenticatedAccount(1L,1L,"admin","ADMIN");
    private final Clock clock=Clock.fixed(Instant.parse("2030-01-01T01:00:00Z"),ZoneId.of("Asia/Ho_Chi_Minh"));
    private final ScheduleStateWriteService service=new ScheduleStateWriteService(db,states,permission,schedules,clock);
    @SuppressWarnings("unchecked")
    @BeforeEach void setup(){
        when(db.query(contains("WITH (UPDLOCK,HOLDLOCK)"),any(RowMapper.class),eq(9L))).thenReturn(List.of(9L));
        schedule(LocalDateTime.now(clock).plusMinutes(1));
        when(states.current(9)).thenReturn(ScheduleStateService.State.ACTIVE);
        when(db.queryForObject(anyString(),eq(Long.class),any(Object[].class))).thenReturn(0L);
        when(db.update(contains("UPDATE dbo.work_schedules SET cancelled_at="),eq(1L),eq("Hủy phân công"),eq(9L),any(LocalDateTime.class))).thenReturn(1);
    }
    private void schedule(LocalDateTime start){when(schedules.get(9)).thenReturn(new ScheduleView(9,5,1L,start.toLocalDate(),start,start.plusHours(4),0,""));}
    @Test void futureScheduleIsSoftCancelledAndAudited(){
        assertEquals(ScheduleStateService.State.CANCELLED,service.cancel(actor,9,"Hủy phân công"));
        verify(db).update(contains("UPDATE dbo.work_schedules SET cancelled_at="),eq(1L),eq("Hủy phân công"),eq(9L),eq(LocalDateTime.now(clock)));
        verify(db).update(contains("'WORK_SCHEDULE_CANCELLED'"),eq(1L),eq("9"),eq("Hủy phân công"),anyString(),eq("{\"state\":\"CANCELLED\"}"));
        verify(db,never()).update(startsWith("DELETE"),any(Object[].class));
    }
    @Test void exactlyAtStartCannotBeCancelled(){schedule(LocalDateTime.now(clock));assertEquals(HttpStatus.CONFLICT,assertThrows(ResponseStatusException.class,()->service.cancel(actor,9,"Hủy phân công")).getStatusCode());verifyNoWrite();}
    @Test void startedScheduleCannotBeCancelled(){schedule(LocalDateTime.now(clock).minusNanos(1));assertThrows(ResponseStatusException.class,()->service.cancel(actor,9,"Hủy phân công"));verifyNoWrite();}
    @Test void existingAttendancePreventsCancellation(){when(db.queryForObject(contains("FROM dbo.attendance_records"),eq(Long.class),eq(9L))).thenReturn(1L);assertThrows(ResponseStatusException.class,()->service.cancel(actor,9,"Hủy phân công"));verifyNoWrite();}
    @Test void pendingChangePreventsCancellation(){when(db.queryForObject(contains("FROM dbo.schedule_change_request_details"),eq(Long.class),eq(9L))).thenReturn(1L);assertThrows(ResponseStatusException.class,()->service.cancel(actor,9,"Hủy phân công"));verifyNoWrite();}
    @Test void editLockedFutureScheduleCanStillBeCancelled(){when(states.current(9)).thenReturn(ScheduleStateService.State.EDIT_LOCKED);assertEquals(ScheduleStateService.State.CANCELLED,service.cancel(actor,9,"Hủy phân công"));}
    @Test void cancelledScheduleCannotBeCancelledAgain(){when(states.current(9)).thenReturn(ScheduleStateService.State.CANCELLED);assertThrows(ResponseStatusException.class,()->service.cancel(actor,9,"Hủy phân công"));verifyNoWrite();}
    @Test void deniedScopeCannotCancel(){doThrow(new ResponseStatusException(HttpStatus.FORBIDDEN)).when(permission).requireCreate(any(),anyLong(),any());assertThrows(ResponseStatusException.class,()->service.cancel(actor,9,"Hủy phân công"));verifyNoWrite();}
    @SuppressWarnings("unchecked")
    @ParameterizedTest @ValueSource(strings={"ADMIN","HR"})
    void editLockBlocksBothAdminAndHr(String role){
        var real=spy(new WorkScheduleService(db,permission));
        var stateService=mock(ScheduleStateService.class);
        ReflectionTestUtils.setField(real,"scheduleStates",stateService);
        doThrow(new ResponseStatusException(HttpStatus.CONFLICT,"Lịch đã khóa chỉnh sửa")).when(stateService).requireEditable(9);
        var start=LocalDateTime.now(clock).plusHours(1);
        doReturn(new ScheduleView(9,5,1L,start.toLocalDate(),start,start.plusHours(4),0,"")).when(real).get(9);
        var principal=new AuthenticatedAccount(1L,1L,"user",role);
        assertThrows(ResponseStatusException.class,()->real.updateWithVerifiedBreaks(principal,9,new ScheduleInput(5L,1L,start,start.plusHours(4),0,"Sửa")));
        verifyNoWrite();
    }
    private void verifyNoWrite(){verify(db,never()).update(anyString(),any(Object[].class));}
}
