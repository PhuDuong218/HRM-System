package com.hrm.service;

import com.hrm.dto.DetailedWorkTimeDTOs.ScheduleWithBreaks;
import com.hrm.dto.WorkTimeDTOs.*;
import com.hrm.security.AuthenticatedAccount;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.json.JsonMapper;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ScheduleWorkspaceServiceTest {
    private final JdbcTemplate db=mock(JdbcTemplate.class);
    private final WorkShiftService shifts=mock(WorkShiftService.class);
    private final WorkScheduleService schedules=mock(WorkScheduleService.class);
    private final ScheduleQueryService queries=mock(ScheduleQueryService.class);
    private final ScheduleWritePermissionService permissions=mock(ScheduleWritePermissionService.class);
    private final DetailedWorkTimeService detailed=mock(DetailedWorkTimeService.class);
    private final ScheduleWorkspaceService service=new ScheduleWorkspaceService(db,shifts,schedules,queries,permissions,detailed,JsonMapper.builder().build());
    private final AuthenticatedAccount admin=new AuthenticatedAccount(1L,1L,"admin","ADMIN");
    private final LocalDate date=LocalDate.of(2030,1,10);

    @SuppressWarnings("unchecked")
    @BeforeEach void actor() {
        when(db.query(contains("JOIN dbo.roles r ON r.role_id=a.role_id WHERE"),any(RowMapper.class),eq(1L)))
                .thenReturn(Collections.singletonList(new Object[]{"ADMIN",1L}));
    }

    @Test void rejectsNegativeEmployeeLookup() {
        var error=assertThrows(ResponseStatusException.class,()->service.employees(admin,"-1",date));
        assertEquals(HttpStatus.BAD_REQUEST,error.getStatusCode());
        verify(db,never()).query(contains("FROM dbo.employees"),any(RowMapper.class),any(Object[].class));
    }

    @Test void rejectsNegativeEmployeeOnCreate() {
        assertThrows(ResponseStatusException.class,()->service.save(admin,null,new ScheduleWorkspaceService.SaveInput(-1L,2L,date,"")));
        verifyNoInteractions(shifts,detailed);
    }

    @Test void derivesSixtyMinuteBreakAndOvernightTimesFromTemplate() {
        var shift=new ShiftView(2,"NIGHT","Ca đêm",LocalTime.of(22,0),LocalTime.of(6,0),1,60,true);
        when(shifts.get(2)).thenReturn(shift);
        var saved=new ScheduleView(10,3,2L,date,date.atTime(22,0),date.plusDays(1).atTime(6,0),60,"");
        when(detailed.createSchedule(eq(admin),any())).thenReturn(saved);
        assertEquals(saved,service.save(admin,null,new ScheduleWorkspaceService.SaveInput(3L,2L,date,"")));
        var capture=ArgumentCaptor.forClass(ScheduleWithBreaks.class);
        verify(detailed).createSchedule(eq(admin),capture.capture());
        assertEquals(60,capture.getValue().schedule().plannedBreakMinutes());
        assertEquals(date.plusDays(1).atTime(6,0),capture.getValue().schedule().plannedEnd());
        verify(permissions).requireCreate(admin,3,date.atTime(22,0));
    }

    @Test void inactiveShiftCannotBeAssigned() {
        when(shifts.get(2)).thenReturn(new ShiftView(2,"S","S",LocalTime.of(8,0),LocalTime.of(12,0),0,0,false));
        assertThrows(ResponseStatusException.class,()->service.save(admin,null,new ScheduleWorkspaceService.SaveInput(3L,2L,date,"")));
        verifyNoInteractions(detailed);
    }

    @Test void ordinaryEditCannotRewriteAttendanceSchedule() {
        when(shifts.get(2)).thenReturn(new ShiftView(2,"S","S",LocalTime.of(8,0),LocalTime.of(12,0),0,0,true));
        when(schedules.get(10)).thenReturn(new ScheduleView(10,3,2L,date,date.atTime(8,0),date.atTime(12,0),0,""));
        when(db.queryForObject(contains("FROM dbo.attendance_records"),eq(Long.class),eq(10L))).thenReturn(1L);
        assertThrows(ResponseStatusException.class,()->service.save(admin,10L,new ScheduleWorkspaceService.SaveInput(3L,2L,date,"")));
        verify(detailed,never()).updateSchedule(any(),anyLong(),any());
    }

    @SuppressWarnings("unchecked")
    @Test void mismatchedRoleAndEmployeeCannotUseWorkspace() {
        when(db.query(contains("JOIN dbo.roles r ON r.role_id=a.role_id WHERE"),any(RowMapper.class),eq(1L)))
                .thenReturn(Collections.singletonList(new Object[]{"HR",5L}));
        assertThrows(ResponseStatusException.class,()->service.context(admin));
    }

    @SuppressWarnings("unchecked")
    @Test void hrLookupUsesDepartmentScopeWithoutRequiringEmployeeAccounts() {
        var hr=new AuthenticatedAccount(1L,5L,"hr","HR");
        when(db.query(contains("JOIN dbo.roles r ON r.role_id=a.role_id WHERE"),any(RowMapper.class),eq(1L)))
                .thenReturn(Collections.singletonList(new Object[]{"HR",5L}));
        service.employees(hr,"Trần",date);
        verify(db).query(contains("dbo.hr_department_scopes"),any(RowMapper.class),eq(date),eq("%Tran%"),eq(date),eq(date),eq(1L));
        verify(db,never()).query(contains("r.role_code='EMPLOYEE'"),any(RowMapper.class),eq(date),anyString(),eq(date),eq(date),eq(1L));
    }

    @Test void negativeShiftFilterRejected() {
        var real=new ScheduleQueryService(db);
        assertThrows(ResponseStatusException.class,()->real.search(admin,null,null,-1L,date,date,0,false));
    }
}
