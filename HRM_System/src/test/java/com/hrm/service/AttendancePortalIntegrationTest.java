package com.hrm.service;

import com.hrm.dto.AttendanceDTOs.*;
import com.hrm.security.AuthenticatedAccount;
import java.nio.file.*;
import java.io.*;
import java.time.*;
import java.util.*;
import javax.sql.DataSource;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.support.JdbcTransactionManager;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.web.server.ResponseStatusException;

/** Opt-in SQL Server integration suite. Every fixture/mutation is rolled back. */
@org.junit.jupiter.api.condition.EnabledIfSystemProperty(named="hrm.attendance.sqlserver-tests",matches="true")
class AttendancePortalIntegrationTest {
    static JdbcTemplate db; static JdbcTransactionManager manager; static AttendancePortalService service;
    static AuthenticatedAccount admin,hr,employee,otherEmployee; static Clock clock;
    static AttendanceService attendance; static AttendancePermissionService permission;static AttendanceSelfClockService self;
    static String resolve(String value){if(value!=null&&value.startsWith("${")&&value.endsWith("}")){String[] parts=value.substring(2,value.length()-1).split(":",2);return System.getenv().getOrDefault(parts[0],parts.length==2?parts[1]:"");}return value;}
    @BeforeAll static void setup()throws Exception{
        Properties props=new Properties();Path resources=Path.of(System.getProperty("hrm.attendance.test-resource-dir","src/main/resources"));
        for(String name:List.of("application.properties","application-local.properties","application-hrm-db.properties"))try(var in=Files.newInputStream(resources.resolve(name))){props.load(in);}
        DriverManagerDataSource ds=new DriverManagerDataSource(resolve(props.getProperty("spring.datasource.url")),resolve(props.getProperty("spring.datasource.username")),resolve(props.getProperty("spring.datasource.password")));
        db=new JdbcTemplate(ds);manager=new JdbcTransactionManager(ds);clock=Clock.system(ZoneId.of("Asia/Ho_Chi_Minh"));
        permission=new AttendancePermissionService(db);attendance=proxy(new AttendanceService(db,permission));self=proxy(new AttendanceSelfClockService(db,clock));service=proxy(new AttendancePortalService(db,permission,attendance,self,clock,7,3));
        admin=principal("ADMIN",0);hr=principal("HR",0);employee=principal("EMPLOYEE",0);otherEmployee=principal("EMPLOYEE",1);
    }
    @SuppressWarnings("unchecked") static <T>T proxy(T target){ProxyFactory factory=new ProxyFactory(target);factory.setProxyTargetClass(true);factory.addAdvice(new TransactionInterceptor(manager,new AnnotationTransactionAttributeSource()));return (T)factory.getProxy();}
    static AuthenticatedAccount principal(String role,int offset){return db.query("SELECT a.account_id,a.employee_id,a.username,r.role_code FROM dbo.accounts a JOIN dbo.roles r ON r.role_id=a.role_id WHERE a.is_active=1 AND r.role_code=? ORDER BY a.account_id OFFSET ? ROWS FETCH NEXT 1 ROWS ONLY",(rs,n)->new AuthenticatedAccount(rs.getLong(1),rs.getObject(2,Long.class),rs.getString(3),rs.getString(4)),role,offset).get(0);}
    void rollback(Runnable check){new TransactionTemplate(manager).executeWithoutResult(status->{try{check.run();}finally{status.setRollbackOnly();}});}
    long fixture(AuthenticatedAccount owner,int days){LocalDate date=LocalDate.now(clock).minusDays(days);LocalDateTime start=date.atTime(0,5);LocalDateTime end=start.plusMinutes(30);return db.queryForObject("INSERT dbo.work_schedules(employee_id,shift_id,work_date,planned_start,planned_end,planned_break_minutes,note,created_by,break_detail_status) OUTPUT INSERTED.schedule_id VALUES(?,NULL,?,?,?,0,N'ROLLBACK-ONLY attendance test',?,'VERIFIED')",Long.class,owner.employeeId(),date,start,end,admin.accountId());}
    AttendanceView manual(long sid,AuthenticatedAccount owner,int days){LocalDate date=LocalDate.now(clock).minusDays(days);return service.manual(admin,new AttendanceInput(owner.employeeId(),sid,date,date.atTime(0,5),date.atTime(0,35),0,"DI_LAM","Kiểm thử rollback"));}
    AttendancePortalService.Proposal proposal(AttendanceView a,String type){return new AttendancePortalService.Proposal(type,a.attendanceId(),a.actualStart(),a.actualEnd()==null?a.actualStart().plusMinutes(25):a.actualEnd().minusMinutes(1),0,"DI_LAM","Đề nghị điều chỉnh");}
    @Test void partialVietnameseNamesMatchWithOrWithoutAccents(){rollback(()->{
        db.update("UPDATE dbo.employees SET full_name=N'Trần Thị A' WHERE employee_id=?",employee.employeeId());
        long sid=fixture(employee,1);LocalDate date=LocalDate.now(clock).minusDays(1);
        for(String query:List.of("tr","t","a","th","tran","TRẦN","thi","thị")){
            var result=service.search(admin,false,date,date,query,null,null,null,0);
            assertTrue(result.items().stream().anyMatch(row->row.scheduleId()==sid),query+" must match part of the employee name");
        }
        assertEquals(0,service.search(admin,false,date,date,"not-a-real-employee-xyz",null,null,null,0).totalElements());
    });}
    @Test void attendanceDetailShowsActualCreatorAndConfirmer(){rollback(()->{
        long sid=fixture(employee,1);var a=manual(sid,employee,1);
        assertEquals(admin.username(),service.detail(admin,false,sid).row().enteredByLabel());
        assertNull(service.detail(admin,false,sid).row().confirmedByLabel());
        service.verify(admin,a.attendanceId(),true,null);
        assertEquals(admin.username(),service.detail(admin,false,sid).row().confirmedByLabel());
        service.verify(admin,a.attendanceId(),false,"Đối soát lại");
        assertNull(service.detail(admin,false,sid).row().confirmedByLabel());
    });}
    @Test void rolesAndReadScopes(){rollback(()->{
        assertFalse(service.context(admin).mine());assertTrue(service.context(hr).mine());assertThrows(ResponseStatusException.class,()->service.search(employee,false,LocalDate.now(clock).minusDays(30),LocalDate.now(clock),null,null,null,null,0));
        assertThrows(ResponseStatusException.class,()->service.search(admin,true,LocalDate.now(clock),LocalDate.now(clock),null,null,null,null,0));
        var page=service.search(admin,false,LocalDate.now(clock).minusDays(30),LocalDate.now(clock),null,null,null,null,0);assertNotNull(page.stats());assertTrue(page.totalElements()>=page.items().size());
        var own=service.search(hr,true,LocalDate.now(clock).minusDays(30),LocalDate.now(clock),null,null,null,null,0);assertTrue(own.items().stream().allMatch(r->r.employeeId()==hr.employeeId()));
        var scoped=service.search(hr,false,LocalDate.now(clock).minusDays(30),LocalDate.now(clock),null,null,null,null,0);for(var row:scoped.items())permission.requireAttendanceAccess(hr.accountId(),row.employeeId(),row.workDate(),AttendancePermissionService.MANAGE);
        assertThrows(ResponseStatusException.class,()->service.search(admin,false,LocalDate.now(clock),LocalDate.now(clock),"-1",null,null,null,0));
    });}
    @Test void manualConfirmUnconfirmAndAudit(){rollback(()->{
        long sid=fixture(employee,1);var a=manual(sid,employee,1);assertEquals("CHUA_XAC_NHAN",a.verificationStatus());
        service.verify(admin,a.attendanceId(),true,null);assertEquals("DA_XAC_NHAN",attendance.get(a.attendanceId()).verificationStatus());
        assertThrows(ResponseStatusException.class,()->service.correct(admin,a.attendanceId(),new AttendanceCorrection(a.actualStart(),a.actualEnd(),0,"DI_LAM","Sửa trực tiếp bị chặn")));
    });rollback(()->{long sid=fixture(employee,1);var a=manual(sid,employee,1);service.verify(admin,a.attendanceId(),true,null);service.verify(admin,a.attendanceId(),false,"Cần đối soát lại");assertEquals("CHUA_XAC_NHAN",attendance.get(a.attendanceId()).verificationStatus());assertTrue(service.detail(admin,false,sid).audit().size()>=3);});}
    @Test void acceptedConfirmedCorrectionAppliesAtomically(){rollback(()->{
        long sid=fixture(employee,1);var a=manual(sid,employee,1);service.verify(admin,a.attendanceId(),true,null);a=attendance.get(a.attendanceId());long id=service.submit(employee,proposal(a,"CONFIRMED_CORRECTION"));
        service.receive(admin,id);service.decide(admin,id,new AttendancePortalService.Decision(true,null,"Đã kiểm tra"));
        assertEquals("DA_CHAP_NHAN",service.request(admin,false,id).request().status());assertEquals(a.actualEnd().minusMinutes(1),attendance.get(a.attendanceId()).actualEnd());assertEquals("DA_XAC_NHAN",attendance.get(a.attendanceId()).verificationStatus());
    });}
    @Test void rejectedRequestPreservesAttendance(){rollback(()->{long sid=fixture(employee,1);var a=manual(sid,employee,1);service.verify(admin,a.attendanceId(),true,null);long id=service.submit(employee,proposal(attendance.get(a.attendanceId()),"CONFIRMED_CORRECTION"));service.receive(admin,id);service.decide(admin,id,new AttendancePortalService.Decision(false,null,"Không đủ căn cứ"));assertEquals(a.actualEnd(),attendance.get(a.attendanceId()).actualEnd());assertEquals("TU_CHOI",service.request(admin,false,id).request().status());});}
    @Test void singleOpenRequestAndNoSelfApproval(){rollback(()->{long sid=fixture(employee,1);var a=manual(sid,employee,1);service.verify(admin,a.attendanceId(),true,null);var proposal=proposal(attendance.get(a.attendanceId()),"CONFIRMED_CORRECTION");service.submit(employee,proposal);assertThrows(ResponseStatusException.class,()->service.submit(employee,proposal));});rollback(()->{long sid=fixture(hr,1);var a=manual(sid,hr,1);service.verify(admin,a.attendanceId(),true,null);long id=service.submit(hr,proposal(attendance.get(a.attendanceId()),"CONFIRMED_CORRECTION"));assertThrows(ResponseStatusException.class,()->service.receive(hr,id));});}
    @Test void missingCheckoutRequiresReceiptAndReasonForChangedEnd(){rollback(()->{long sid=fixture(employee,1);LocalDate date=LocalDate.now(clock).minusDays(1);var a=service.manual(admin,new AttendanceInput(employee.employeeId(),sid,date,date.atTime(0,5),null,0,"DI_LAM","Quên giờ ra"));long id=service.submit(employee,proposal(a,"MISSING_CHECKOUT"));service.receive(admin,id);service.decide(admin,id,new AttendancePortalService.Decision(true,a.actualStart().plusMinutes(20),"Giờ ra xác minh khác đề nghị"));assertEquals(a.actualStart().plusMinutes(20),attendance.get(a.attendanceId()).actualEnd());assertEquals("CHUA_XAC_NHAN",attendance.get(a.attendanceId()).verificationStatus());});}
    @Test void staleOriginalCannotBeApproved(){rollback(()->{long sid=fixture(employee,1);var a=manual(sid,employee,1);service.verify(admin,a.attendanceId(),true,null);long id=service.submit(employee,proposal(attendance.get(a.attendanceId()),"CONFIRMED_CORRECTION"));service.receive(admin,id);db.update("UPDATE dbo.attendance_records SET correction_reason=N'changed' WHERE attendance_id=?",a.attendanceId());assertThrows(ResponseStatusException.class,()->service.decide(admin,id,new AttendancePortalService.Decision(true,null,"")));});}
    @Test void expiredMissingCheckoutCannotBeSubmitted(){rollback(()->{long sid=fixture(employee,8);LocalDate date=LocalDate.now(clock).minusDays(8);var a=service.manual(admin,new AttendanceInput(employee.employeeId(),sid,date,date.atTime(0,5),null,0,"DI_LAM","Quên giờ ra"));assertThrows(ResponseStatusException.class,()->service.submit(employee,proposal(a,"MISSING_CHECKOUT")));});}
    @Test void employeeCannotReadOtherAttendance(){rollback(()->{long sid=fixture(employee,1);manual(sid,employee,1);assertThrows(ResponseStatusException.class,()->service.detail(otherEmployee,true,sid));});}
    AuthenticatedAccount scopedEmployee(){return db.query("""
        SELECT TOP(1) a.account_id,a.employee_id,a.username,r.role_code FROM dbo.accounts a JOIN dbo.roles r ON r.role_id=a.role_id
        WHERE a.is_active=1 AND r.role_code='EMPLOYEE' AND EXISTS(SELECT 1 FROM dbo.employee_assignments ea
        JOIN dbo.hr_department_scopes hs ON hs.department_id=ea.department_id WHERE ea.employee_id=a.employee_id
        AND ea.effective_from<=? AND (ea.effective_to IS NULL OR ea.effective_to>?) AND hs.hr_account_id=? AND hs.revoked_at IS NULL
        AND hs.effective_from<=SYSUTCDATETIME() AND (hs.effective_to IS NULL OR hs.effective_to>SYSUTCDATETIME())) ORDER BY a.account_id
        """,(rs,n)->new AuthenticatedAccount(rs.getLong(1),rs.getLong(2),rs.getString(3),rs.getString(4)),LocalDate.now(clock).minusDays(1),LocalDate.now(clock).minusDays(1),hr.accountId()).get(0);}
    @Test void hrCannotConfirmAnEarlierEditEvenAfterAdminEditedAgain(){rollback(()->{
        db.update("""
          INSERT dbo.hr_department_scopes(hr_account_id,department_id,effective_from,created_by)
          SELECT ?,department_id,DATEADD(day,-1,SYSUTCDATETIME()),? FROM dbo.employee_assignments
          WHERE employee_id=? AND effective_from<=? AND (effective_to IS NULL OR effective_to>?)
          """,hr.accountId(),admin.accountId(),employee.employeeId(),LocalDate.now(clock).minusDays(1),LocalDate.now(clock).minusDays(1));
        var owner=scopedEmployee();long sid=fixture(owner,1);var a=manual(sid,owner,1);
        service.correct(hr,a.attendanceId(),new AttendanceCorrection(a.actualStart(),a.actualEnd(),0,"DI_LAM","HR sửa lần đầu"));
        service.correct(admin,a.attendanceId(),new AttendanceCorrection(a.actualStart(),a.actualEnd(),0,"DI_LAM","ADMIN sửa sau"));
        assertFalse(service.detail(hr,false,sid).row().canConfirm());
        assertThrows(ResponseStatusException.class,()->service.verify(hr,a.attendanceId(),true,null));
    });}
    @Test void hrAttendanceOnlyAdminCanManage(){rollback(()->{long sid=fixture(hr,1);manual(sid,hr,1);assertThrows(ResponseStatusException.class,()->service.detail(hr,false,sid));});}
    @Test void invalidatedAttendanceIsNotCountedAsConfirmed(){rollback(()->{long sid=fixture(employee,1);var a=manual(sid,employee,1);service.verify(admin,a.attendanceId(),true,null);var before=service.search(admin,false,a.workDate(),a.workDate(),null,null,null,null,0);db.update("UPDATE dbo.attendance_records SET invalidated_at=SYSUTCDATETIME(),invalidated_by=?,invalidation_reason=N'test' WHERE attendance_id=?",admin.accountId(),a.attendanceId());var after=service.search(admin,false,a.workDate(),a.workDate(),null,null,null,null,0);assertEquals(before.stats().confirmed()-1,after.stats().confirmed());assertNull(service.detail(admin,false,sid).row().attendanceId());});}
    @Test void overlappingManualAttendanceIsRejected(){rollback(()->{long sid=fixture(employee,1);manual(sid,employee,1);long other=fixture(employee,1);assertThrows(ResponseStatusException.class,()->manual(other,employee,1));});}
    @Test void payrollLockBlocksManualEntry(){rollback(()->{LocalDate date=db.queryForObject("SELECT TOP(1) p.period_end FROM dbo.payroll_periods p JOIN dbo.payroll_batches b ON b.period_id=p.period_id WHERE b.locked_at IS NOT NULL OR b.published_at IS NOT NULL OR b.status='DA_CHOT' ORDER BY p.period_end DESC",LocalDate.class);int days=(int)java.time.temporal.ChronoUnit.DAYS.between(date,LocalDate.now(clock));long sid=fixture(employee,days);assertThrows(ResponseStatusException.class,()->manual(sid,employee,days));});}
    @Test void staleOrdinaryEditVersionIsRejected(){rollback(()->{long sid=fixture(employee,1);var a=manual(sid,employee,1);String version=service.detail(admin,false,sid).row().version();service.correct(admin,a.attendanceId(),new AttendanceCorrection(a.actualStart(),a.actualEnd(),0,"DI_LAM","Một người sửa trước"));assertThrows(ResponseStatusException.class,()->service.correctVersioned(admin,a.attendanceId(),new AttendanceCorrection(a.actualStart(),a.actualEnd(),0,"DI_LAM","Biểu mẫu cũ"),version));});}
    @org.junit.jupiter.params.ParameterizedTest @org.junit.jupiter.params.provider.ValueSource(strings={"HR","EMPLOYEE"}) void hrCanClockOwnShiftUsingServerTime(String role){rollback(()->{var owner=role.equals("HR")?hr:employee;LocalDateTime start=LocalDateTime.now(clock).minusMinutes(1);long sid=db.queryForObject("INSERT dbo.work_schedules(employee_id,shift_id,work_date,planned_start,planned_end,planned_break_minutes,note,created_by,break_detail_status) OUTPUT INSERTED.schedule_id VALUES(?,NULL,?,?,?,0,N'ROLLBACK-ONLY self clock',?,'VERIFIED')",Long.class,owner.employeeId(),start.toLocalDate(),start,start.plusMinutes(30),admin.accountId());var event=service.clock(owner,sid,true);assertTrue(event.recordedAt().isAfter(start));assertNotNull(service.detail(owner,true,sid).row().actualStart());assertEquals(db.queryForObject("SELECT full_name FROM dbo.employees WHERE employee_id=?",String.class,owner.employeeId()),service.detail(owner,true,sid).row().enteredByLabel());});}
    @Test void deadlineWaitsForOfficialCalendar(){assertNull(new AttendancePortalDeadlinePolicy(false,"", "SATURDAY,SUNDAY").processingDeadline(LocalDate.of(2026,12,24)));assertEquals(LocalDate.of(2026,12,29),new AttendancePortalDeadlinePolicy(true,"2026-12-25", "SATURDAY,SUNDAY").processingDeadline(LocalDate.of(2026,12,24)).toLocalDate());}
    @Test void failureAfterAttendanceWriteRollsBackAttendanceRequestAndAudit(){rollback(()->{long sid=fixture(employee,1);var a=manual(sid,employee,1);service.verify(admin,a.attendanceId(),true,null);long id=service.submit(employee,proposal(attendance.get(a.attendanceId()),"CONFIRMED_CORRECTION"));service.receive(admin,id);
        JdbcTemplate failing=spy(db);doThrow(new IllegalStateException("Injected audit failure")).when(failing).update(startsWith("INSERT dbo.audit_logs(actor_account_id,action,target_table,target_record_key,reason)"),any(Object[].class));
        var broken=proxy(new AttendancePortalService(failing,permission,attendance,self,clock,7,3));TransactionTemplate nested=new TransactionTemplate(manager);nested.setPropagationBehavior(TransactionDefinition.PROPAGATION_NESTED);
        assertThrows(IllegalStateException.class,()->nested.executeWithoutResult(status->broken.decide(admin,id,new AttendancePortalService.Decision(true,null,""))));
        assertEquals(a.actualEnd(),attendance.get(a.attendanceId()).actualEnd());assertEquals("DANG_XU_LY",service.request(admin,false,id).request().status());
    });}
    @Test void thirtyMinuteEarlyWindowAndLateTolerance(){LocalDateTime start=LocalDateTime.of(2026,11,1,8,0);assertTrue(AttendanceClockPolicy.checkIn(start,start.plusHours(4),start.minusMinutes(30)).allowed());assertFalse(AttendanceClockPolicy.checkIn(start,start.plusHours(4),start.minusMinutes(30).minusNanos(1)).allowed());assertTrue(AttendanceClockPolicy.checkIn(start,start.plusHours(4),start.plusMinutes(16)).allowed());assertFalse(AttendanceClockPolicy.checkIn(start,start.plusHours(4),start.plusHours(4)).allowed());}
}
