package com.hrm.service;

import com.hrm.dto.WorkTimeDTOs.ScheduleView;
import com.hrm.security.AuthenticatedAccount;
import java.time.*;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** The deadline belongs to cancellation, not to edit locking. */
class ScheduleLockDeadlineTest {
    private final JdbcTemplate db=mock(JdbcTemplate.class);
    private final ScheduleStateService states=mock(ScheduleStateService.class);
    private final ScheduleWritePermissionService permission=mock(ScheduleWritePermissionService.class);
    private final WorkScheduleService schedules=mock(WorkScheduleService.class);
    private final AuthenticatedAccount actor=new AuthenticatedAccount(1L,1L,"admin","ADMIN");
    private final Clock clock=Clock.fixed(Instant.parse("2030-01-01T01:00:00Z"),ZoneId.of("Asia/Ho_Chi_Minh"));
    private final ScheduleStateWriteService service=new ScheduleStateWriteService(db,states,permission,schedules,clock);
    @SuppressWarnings("unchecked")
    private void setup(LocalDateTime start,boolean unlocking){
        when(db.query(contains("WITH (UPDLOCK,HOLDLOCK)"),any(RowMapper.class),eq(9L))).thenReturn(List.of(9L));
        when(schedules.get(9)).thenReturn(new ScheduleView(9,5,1L,start.toLocalDate(),start,start.plusHours(4),0,""));
        when(states.current(9)).thenReturn(unlocking?ScheduleStateService.State.EDIT_LOCKED:ScheduleStateService.State.ACTIVE,
                unlocking?ScheduleStateService.State.ACTIVE:ScheduleStateService.State.EDIT_LOCKED);
    }
    @Test void futureScheduleCanBeEditLocked(){setup(LocalDateTime.now(clock).plusMinutes(1),false);assertEquals(ScheduleStateService.State.EDIT_LOCKED,service.lockEdit(actor,9,"Bảo vệ lịch"));}
    @Test void editLockIsAllowedExactlyAtStart(){setup(LocalDateTime.now(clock),false);assertEquals(ScheduleStateService.State.EDIT_LOCKED,service.lockEdit(actor,9,"Bảo vệ lịch"));}
    @Test void editLockIsAllowedAfterStart(){setup(LocalDateTime.now(clock).minusMinutes(1),false);assertEquals(ScheduleStateService.State.EDIT_LOCKED,service.lockEdit(actor,9,"Bảo vệ lịch"));}
    @Test void unlockDoesNotCancelSchedule(){setup(LocalDateTime.now(clock).minusMinutes(1),true);assertEquals(ScheduleStateService.State.ACTIVE,service.unlockEdit(actor,9,"Mở khóa sửa"));verify(db,never()).update(contains("cancelled_at="),any(Object[].class));}
}
