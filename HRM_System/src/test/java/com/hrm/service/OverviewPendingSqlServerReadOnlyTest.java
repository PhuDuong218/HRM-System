package com.hrm.service;

import com.hrm.security.AuthenticatedAccount;
import java.io.FileInputStream;
import java.sql.DriverManager;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

/** Opt-in SELECT-only probe. Never starts the application, creates fixtures or applies migrations. */
@EnabledIfSystemProperty(named="hrm.overview-pending.readonly",matches="true")
class OverviewPendingSqlServerReadOnlyTest {
    @Test void countsMatchTheAllTimeSourceListsForAdminAndHr() throws Exception {
        var config=new Properties();
        try(var input=new FileInputStream("src/main/resources/application-local.properties")){config.load(input);}
        try(var connection=DriverManager.getConnection(config.getProperty("spring.datasource.url"),config.getProperty("spring.datasource.username"),config.getProperty("spring.datasource.password"))){
            connection.setReadOnly(true);
            var db=new JdbcTemplate(new SingleConnectionDataSource(connection,true));
            var clock=Clock.system(ZoneId.of("Asia/Ho_Chi_Minh"));
            var permissions=new AttendancePermissionService(db);
            var attendance=new AttendancePortalService(db,permissions,new AttendanceService(db,permissions),new AttendanceSelfClockService(db,clock),clock,7,3);
            ReflectionTestUtils.setField(attendance,"deadlinePolicy",new AttendancePortalDeadlinePolicy(false,"","SATURDAY,SUNDAY"));
            var otPermissions=new OvertimePermissionService(db);
            var ledger=new OvertimeDailyLedger(db);var recognition=new OvertimeRecognizedMinutes(db);
            var overtime=new OvertimeWorkspaceService(db,otPermissions,new OvertimeService(db,otPermissions,ledger,recognition),new OvertimeRequestWorkflowService(db,otPermissions),recognition,ledger,clock);
            var scopes=new PersonnelScopeService(db);
            var heads=new DepartmentHeadAccessService(db);ReflectionTestUtils.setField(heads,"personnelScope",scopes);
            var requests=new EmployeeRequestService(db,new PersonnelAccessService(db,heads),new PersonnelWorkflowSupport(db,clock,mock(AuditLogService.class)),new AssignmentGuardService(db));
            ReflectionTestUtils.setField(requests,"personnelScope",scopes);
            var overview=new OverviewPendingService(attendance,requests,overtime,otPermissions,clock);
            var actors=db.query("""
                SELECT a.account_id,a.employee_id,a.username,r.role_code FROM dbo.accounts a
                JOIN dbo.roles r ON r.role_id=a.role_id
                WHERE a.is_active=1 AND r.role_code IN ('ADMIN','HR') ORDER BY r.role_code,a.account_id
                """,(r,n)->new AuthenticatedAccount(r.getLong(1),r.getObject(2,Long.class),r.getString(3),r.getString(4)));
            assertFalse(actors.isEmpty());
            for(String role:List.of("ADMIN","HR")){
                var actor=actors.stream().filter(a->role.equals(a.roleCode())).findFirst().orElse(null);
                if(actor==null)continue;
                SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(actor,null,List.of()));
                var result=overview.get(actor);
                for(var item:List.of(result.attendance(),result.leave(),result.overtimeApproval(),result.overtimeActual())){
                    assertNotEquals("ERROR",item.state());
                    if(item.state().equals("READY"))assertTrue(item.count()>=0);else assertNull(item.count());
                }
                if(result.attendance().state().equals("READY")){
                    var page=attendance.pending(actor,0);assertEquals(result.attendance().count().longValue(),page.totalElements());
                    assertTrue(page.items().stream().allMatch(row->row.attendanceId()!=null&&"CHUA_XAC_NHAN".equals(row.verificationStatus())));
                }
                if(result.leave().state().equals("READY")){
                    var page=requests.list(false,"CHO_DUYET","NGHI_PHEP",0,10,actor);
                    assertEquals(result.leave().count().longValue(),page.getTotalElements());
                    assertTrue(page.getContent().stream().allMatch(row->"CHO_DUYET".equals(row.get("status"))&&row.get("withdrawnAt")==null));
                }
                for(String kind:List.of("approval","actual")){
                    var item=kind.equals("approval")?result.overtimeApproval():result.overtimeActual();
                    if(!item.state().equals("READY"))continue;
                    var page=overtime.pending(actor,kind,0);assertEquals(item.count().longValue(),page.totalElements());
                    assertTrue(page.items().stream().allMatch(row->kind.equals("approval")?row.ot().status().equals("CHO_DUYET"):row.ot().status().equals("DA_DUYET")&&row.ot().confirmedBy()==null));
                }
            }
        } finally {SecurityContextHolder.clearContext();}
    }
}
