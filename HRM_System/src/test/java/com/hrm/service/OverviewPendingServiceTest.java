package com.hrm.service;

import com.hrm.security.AuthenticatedAccount;
import java.time.*;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class OverviewPendingServiceTest {
    @Test void leaveBacklogIncludesOlderRequestsAndExcludesWithdrawnApprovedAndOtherScopes() {
        var db=new org.springframework.jdbc.core.JdbcTemplate(new org.springframework.jdbc.datasource.DriverManagerDataSource("jdbc:h2:mem:pending_leave;MODE=MSSQLServer;DB_CLOSE_DELAY=-1","sa","")) {
            // H2 COUNT already returns bigint; it does not implement SQL Server's COUNT_BIG spelling.
            @Override public <T> T queryForObject(String sql,Class<T> type,Object... args) {
                return super.queryForObject(sql.replace("COUNT_BIG(*)","COUNT(*)"),type,args);
            }
        };
        db.execute("CREATE SCHEMA dbo");
        db.execute("CREATE TABLE dbo.employee_requests(request_id bigint,employee_id bigint,request_type varchar(30),status varchar(30),submitted_at timestamp)");
        db.execute("CREATE TABLE dbo.employee_request_withdrawals(request_id bigint)");
        db.update("INSERT INTO dbo.employee_requests VALUES(1,3,'NGHI_PHEP','CHO_DUYET','2020-01-01'),(2,3,'NGHI_PHEP','CHO_DUYET','2026-11-10'),(3,3,'NGHI_PHEP','DA_DUYET','2026-11-10'),(4,3,'NGHI_PHEP','CHO_DUYET','2026-11-10'),(5,4,'NGHI_PHEP','CHO_DUYET','2026-11-10'),(6,3,'NGHI_VIEC','CHO_DUYET','2026-11-10')");
        db.update("INSERT INTO dbo.employee_request_withdrawals VALUES(4)");
        var access=mock(PersonnelAccessService.class);when(access.manager(actor)).thenReturn(new PersonnelAccessService.Actor(1,2,"HR"));
        var scope=mock(PersonnelScopeService.class);when(scope.sqlEmployees("r.employee_id")).thenReturn("r.employee_id IN (3)");
        var source=new EmployeeRequestService(db,access,mock(PersonnelWorkflowSupport.class),mock(AssignmentGuardService.class));
        org.springframework.test.util.ReflectionTestUtils.setField(source,"personnelScope",scope);
        org.springframework.security.core.context.SecurityContextHolder.getContext().setAuthentication(new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(actor,null,java.util.List.of()));
        try{assertEquals(2,source.pendingLeaveCount(actor));}
        finally{org.springframework.security.core.context.SecurityContextHolder.clearContext();}
    }
    private final AttendancePortalService attendance=mock(AttendancePortalService.class);
    private final EmployeeRequestService requests=mock(EmployeeRequestService.class);
    private final OvertimeWorkspaceService overtime=mock(OvertimeWorkspaceService.class);
    private final OvertimePermissionService identities=mock(OvertimePermissionService.class);
    private final Clock clock=Clock.fixed(Instant.parse("2026-11-10T00:00:00Z"),ZoneOffset.UTC);
    private final AuthenticatedAccount actor=new AuthenticatedAccount(1L,2L,"hr","HR");
    private OverviewPendingService service(){return new OverviewPendingService(attendance,requests,overtime,identities,clock);}
    private void active(){when(identities.active(actor)).thenReturn(new OvertimePermissionService.VerifiedActor(1,2L,"HR"));}

    @Test void currentBacklogDelegatesToSourceScopesWithoutDateFilters() {
        active();when(attendance.pendingCount(actor)).thenReturn(4L);when(requests.pendingLeaveCount(actor)).thenReturn(3L);
        when(overtime.pendingCount(actor,"approval")).thenReturn(2L);when(overtime.pendingCount(actor,"actual")).thenReturn(5L);
        var result=service().get(actor);
        assertEquals(clock.instant(),result.updatedAt());
        assertEquals(4L,result.attendance().count());assertEquals(3L,result.leave().count());
        assertEquals(2L,result.overtimeApproval().count());assertEquals(5L,result.overtimeActual().count());
        verify(requests).pendingLeaveCount(actor);verify(attendance).pendingCount(actor);
    }
    @Test void deniedAndFailedSourcesStayDistinctFromZeroAndDoNotHideOtherSources() {
        active();when(attendance.pendingCount(actor)).thenThrow(new ResponseStatusException(HttpStatus.FORBIDDEN));
        when(requests.pendingLeaveCount(actor)).thenThrow(new org.springframework.dao.DataAccessResourceFailureException("offline"));
        when(overtime.pendingCount(actor,"approval")).thenReturn(0L);when(overtime.pendingCount(actor,"actual")).thenReturn(2L);
        var result=service().get(actor);
        assertEquals("DENIED",result.attendance().state());assertNull(result.attendance().count());
        assertEquals("ERROR",result.leave().state());assertNull(result.leave().count());
        assertEquals("READY",result.overtimeApproval().state());assertEquals(0L,result.overtimeApproval().count());
        assertEquals(2L,result.overtimeActual().count());
    }
    @Test void nonManagersAndChangedAccountsCannotReadAnyQueues() {
        when(identities.active(actor)).thenReturn(new OvertimePermissionService.VerifiedActor(1,2L,"EMPLOYEE"));
        assertThrows(ResponseStatusException.class,()->service().get(actor));
        verifyNoInteractions(attendance,requests,overtime);
        when(identities.active(actor)).thenThrow(new ResponseStatusException(HttpStatus.FORBIDDEN));
        assertThrows(ResponseStatusException.class,()->service().get(actor));
        verifyNoInteractions(attendance,requests,overtime);
    }
}
