package com.hrm.hrm.service;

import java.math.BigDecimal;
import java.security.Principal;
import java.time.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import com.hrm.dto.*;
import com.hrm.dto.EmployeeRequestDTOs.*;
import com.hrm.service.*;
import jakarta.persistence.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.*;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

/** Actual SQL Server, fixture rollback, no deletion of existing data. */
@SpringBootTest(properties={"spring.jpa.show-sql=false", "hrm.contracts.expiry-job-enabled=false"})
class EmployeeRequestWorkflowIntegrationTest {
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager transactions;
    @Autowired EmployeeRequestService requests;
    @Autowired PersonnelAccessService access;
    @Autowired PersonnelCatalogService catalogs;
    @Autowired LeaveEntitlementService entitlements;
    @Autowired QualificationLevelService levels;
    @Autowired EmployeeQualificationService qualifications;
    @MockitoSpyBean PersonnelWorkflowSupport support;
    @MockitoBean(name="hrmBusinessClock") Clock clock;
    @PersistenceContext EntityManager em;
    final AtomicReference<Instant> now=new AtomicReference<>(Instant.parse("2026-11-10T02:00:00Z"));
    final LocalDate day=LocalDate.of(2026,11,10);
    String tag;long employee,other,adminId,type,department,position,limit;
    Principal admin,worker,stranger;
    @BeforeEach void setup() {
        assertEquals("hrm_test",jdbc.queryForObject("SELECT DB_NAME()",String.class));
        now.set(Instant.parse("2026-11-10T02:00:00Z"));
        when(clock.instant()).thenAnswer(i->now.get());when(clock.getZone()).thenReturn(ZoneId.of("Asia/Ho_Chi_Minh"));
        when(clock.withZone(any())).thenAnswer(i->Clock.fixed(now.get(),i.getArgument(0)));
    }
    private long employee(String suffix) {
        jdbc.update("INSERT dbo.employees(employee_code,full_name,hire_date) VALUES(?,?,?)",tag+suffix,"G2 test",day.minusYears(1));
        return jdbc.queryForObject("SELECT employee_id FROM dbo.employees WHERE employee_code=?",Long.class,tag+suffix);
    }
    private Principal account(long owner,String role,String suffix) {
        String username=tag+suffix;
        jdbc.update("INSERT dbo.accounts(employee_id,role_id,username,password_hash,is_active) SELECT ?,role_id,?,REPLICATE('x',60),1 FROM dbo.roles WHERE role_code=?",owner,username,role);
        return ()->username;
    }
    private void fixture() {
        tag="G2T-"+UUID.randomUUID().toString().substring(0,10);
        long manager=employee("A");employee=employee("E");other=employee("O");
        admin=account(manager,"ADMIN","A");worker=account(employee,"EMPLOYEE","E");stranger=account(other,"EMPLOYEE","O");
        adminId=jdbc.queryForObject("SELECT account_id FROM dbo.accounts WHERE username=?",Long.class,admin.getName());
        jdbc.update("INSERT dbo.departments(department_code,department_name) VALUES(?,N'G2 department')",tag);
        department=jdbc.queryForObject("SELECT department_id FROM dbo.departments WHERE department_code=?",Long.class,tag);
        jdbc.update("INSERT dbo.positions(position_code,position_name) VALUES(?,N'G2 position')",tag);
        position=jdbc.queryForObject("SELECT position_id FROM dbo.positions WHERE position_code=?",Long.class,tag);
        jdbc.update("INSERT dbo.employee_assignments(employee_id,department_id,position_id,effective_from) VALUES(?,?,?,?)",employee,department,position,day.minusMonths(1));
        jdbc.update("INSERT dbo.leave_types(type_code,type_name,is_paid) VALUES(?,N'G2 annual',1)",tag);
        type=jdbc.queryForObject("SELECT leave_type_id FROM dbo.leave_types WHERE type_code=?",Long.class,tag);
        requests.policy(type,new Policy(true),admin);
        jdbc.update("INSERT dbo.leave_entitlements(employee_id,leave_type_id,period_start,period_end,entitled_hours) VALUES(?,?,?,?,?)",employee,type,day.minusDays(1),day.plusMonths(1),16);
        limit=jdbc.queryForObject("SELECT entitlement_id FROM dbo.leave_entitlements WHERE employee_id=?",Long.class,employee);
        schedule(day.plusDays(1),0);schedule(day.plusDays(2),60);
    }
    private void schedule(LocalDate date,int pause) {
        jdbc.update("INSERT dbo.work_schedules(employee_id,work_date,planned_start,planned_end,planned_break_minutes) VALUES(?,?,?,?,?)",employee,date,date.atTime(8,0),date.atTime(17,0),pause);
    }
    private void rollback(Runnable body) {
        new TransactionTemplate(transactions).executeWithoutResult(s->{s.setRollbackOnly();fixture();body.run();});
    }
    private long id(Map<String,Object> request){return EmployeeRequestService.number(request,"requestId");}
    private Leave leave(int offset){return new Leave(type,day.plusDays(offset).atTime(8,0),day.plusDays(offset).atTime(17,0),"G2 request");}
    private void fails(int status,Runnable body){assertEquals(status,assertThrows(ResponseStatusException.class,body::run).getStatusCode().value());}
    private void hours(String expected){assertEquals(0,new BigDecimal(expected).compareTo(requests.used(limit)));}

    @Test void createsHeaderSubtypeAndAuditAtomically(){rollback(()->{
        var row=requests.leave(leave(1),worker);assertEquals("CHO_DUYET",row.get("status"));
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM dbo.leave_request_details WHERE request_id=?",Integer.class,id(row)));
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM dbo.audit_logs WHERE action='LEAVE_REQUEST_CREATE' AND target_record_key=?",Integer.class,""+id(row)));hours("0");
    });}
    @Test void approvesAndCountsNetScheduledHoursOnce(){rollback(()->{
        long request=id(requests.leave(leave(2),worker));requests.approve(request,new Decision(null,"approved"),admin);hours("8");
        assertEquals(0,new BigDecimal("16").compareTo(jdbc.queryForObject("SELECT entitled_hours FROM dbo.leave_entitlements WHERE entitlement_id=?",BigDecimal.class,limit)));
        fails(409,()->requests.approve(request,new Decision(null,null),admin));hours("8");
    });}
    @Test void rejectionDoesNotConsumeAndCannotProcessAgain(){rollback(()->{
        long request=id(requests.leave(leave(1),worker));requests.reject(request,new Rejection("Not appropriate"),admin);hours("0");
        fails(409,()->requests.reject(request,new Rejection("twice"),admin));
    });}
    @Test void ownerWithdrawsPendingRequestWithoutInventingAnotherStatus(){rollback(()->{
        long request=id(requests.leave(leave(1),worker));
        var row=requests.withdraw(request,new Rejection("Đổi lịch nghỉ"),worker);
        assertEquals("CHO_DUYET",row.get("status"));assertNotNull(row.get("withdrawnAt"));hours("0");
        fails(409,()->requests.approve(request,new Decision(null,null),admin));
        assertNotNull(requests.leave(leave(1),worker));
    });}
    @Test void nobodyWithdrawsAnothersRequestOrAnApprovedRequest(){rollback(()->{
        long request=id(requests.leave(leave(1),worker));
        fails(403,()->requests.withdraw(request,new Rejection("not owner"),admin));
        fails(403,()->requests.withdraw(request,new Rejection("not owner"),stranger));
        requests.approve(request,new Decision(null,null),admin);
        fails(409,()->requests.withdraw(request,new Rejection("too late"),worker));hours("9");
    });}
    @Test void withdrawnResignationDoesNotBlockNewSubmission(){rollback(()->{
        long request=id(requests.resign(new Resignation(day.plusDays(5),"First"),worker));
        requests.withdraw(request,new Rejection("Reconsider"),worker);
        assertNotNull(requests.resign(new Resignation(day.plusDays(6),"New"),worker));
    });}
    @Test void withdrawnRequestStaysInOwnerHistoryAndLeavesManagerQueue(){rollback(()->{
        long request=id(requests.leave(leave(1),worker));
        requests.withdraw(request,new Rejection("Đổi kế hoạch"),worker);
        var history=requests.list(true,null,null,0,10,worker);
        assertEquals(1,history.getTotalElements());assertNotNull(history.getContent().get(0).get("withdrawnAt"));
        var queue=requests.list(false,"CHO_DUYET","NGHI_PHEP",0,100,admin);
        assertTrue(queue.getContent().stream().noneMatch(row->id(row)==request));
        fails(409,()->requests.reject(request,new Rejection("Đã rút"),admin));
        fails(409,()->requests.withdraw(request,new Rejection("Lần nữa"),worker));
    });}
    @Test void withdrawalDatabaseGuardRejectsImpersonatingTheOwner(){rollback(()->{
        long request=id(requests.leave(leave(1),worker));
        assertThrows(org.springframework.dao.DataAccessException.class,()->jdbc.update(
            "INSERT dbo.employee_request_withdrawals(request_id,withdrawn_by,reason) VALUES(?,?,?)",request,adminId,"Không phải chủ đơn"));
    });}
    @Test void preventsSelfApprovalAndSelfRejection(){rollback(()->{
        long own=id(requests.resign(new Resignation(day.plusDays(5),"Own request"),admin));
        fails(403,()->requests.approve(own,new Decision(null,null),admin));fails(403,()->requests.reject(own,new Rejection("self"),admin));
    });}
    @Test void employeeCannotApproveOrReadOtherEmployeesRequest(){rollback(()->{
        long request=id(requests.leave(leave(1),worker));fails(403,()->requests.approve(request,new Decision(null,null),stranger));fails(403,()->requests.detail(request,stranger));
        assertEquals(employee,EmployeeRequestService.number(requests.detail(request,worker),"employeeId"));
    });}
    @Test void unauthenticatedAndInactiveAccountsDenied(){rollback(()->{
        fails(401,()->requests.leave(leave(1),null));
        jdbc.update("UPDATE dbo.accounts SET is_active=0 WHERE username=?",worker.getName());fails(403,()->requests.leave(leave(1),worker));
    });}
    @Test void preventsOverlapButAllowsRejectedIntervalAgain(){rollback(()->{
        long request=id(requests.leave(leave(1),worker));fails(409,()->requests.leave(leave(1),worker));
        requests.reject(request,new Rejection("try again"),admin);assertNotNull(requests.leave(leave(1),worker));
    });}
    @Test void insufficientEntitlementRejected(){rollback(()->{
        jdbc.update("UPDATE dbo.leave_entitlements SET entitled_hours=1 WHERE entitlement_id=?",limit);fails(409,()->requests.leave(leave(1),worker));hours("0");
    });}
    @Test void policyMustBeExplicitAndNotInferredFromPaidFlag(){rollback(()->{
        requests.policy(type,new Policy(false),admin);jdbc.update("UPDATE dbo.leave_entitlements SET entitled_hours=0 WHERE entitlement_id=?",limit);
        long request=id(requests.leave(leave(1),worker));requests.approve(request,new Decision(null,null),admin);hours("0");
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM dbo.g2_leave_allocations WHERE request_id=? AND entitlement_id IS NULL",Integer.class,request));
    });}
    @Test void policyCannotChangeAfterRequestsExist(){rollback(()->{
        requests.leave(leave(1),worker);fails(409,()->requests.policy(type,new Policy(false),admin));fails(403,()->requests.policy(type,new Policy(false),worker));
    });}
    @Test void missingPolicyAndInactiveTypeDenied(){rollback(()->{
        jdbc.update("INSERT dbo.leave_types(type_code,type_name) VALUES(?,N'No policy')",tag+"N");long noPolicy=jdbc.queryForObject("SELECT leave_type_id FROM dbo.leave_types WHERE type_code=?",Long.class,tag+"N");
        fails(409,()->requests.leave(new Leave(noPolicy,leave(1).leaveStart(),leave(1).leaveEnd(),"test"),worker));
        jdbc.update("UPDATE dbo.leave_types SET is_active=0 WHERE leave_type_id=?",type);fails(409,()->requests.leave(leave(1),worker));
    });}
    @Test void missingScheduleAndPartialBreakAreNotGuessed(){rollback(()->{
        fails(409,()->requests.leave(leave(10),worker));fails(409,()->requests.leave(new Leave(type,day.plusDays(2).atTime(9,0),day.plusDays(2).atTime(11,0),"partial"),worker));
    });}
    @Test void rejectsPastAndReversedDates(){rollback(()->{
        fails(400,()->requests.leave(leave(-1),worker));fails(400,()->requests.leave(new Leave(type,leave(1).leaveEnd(),leave(1).leaveStart(),"reverse"),worker));
    });}
    @Test void adjacentPartialIntervalsWithoutBreakDoNotOverlap(){rollback(()->{
        var date=day.plusDays(1);
        long a=id(requests.leave(new Leave(type,date.atTime(8,0),date.atTime(9,0),"first hour"),worker));
        long b=id(requests.leave(new Leave(type,date.atTime(9,0),date.atTime(10,0),"second hour"),worker));
        requests.approve(a,new Decision(null,null),admin);requests.approve(b,new Decision(null,null),admin);hours("2");
    });}
    @Test void overnightLeaveUsesScheduleWorkDateAndDeductsNetHours(){rollback(()->{
        LocalDate workDate=day.plusDays(3);
        var start=workDate.atTime(22,0);var end=workDate.plusDays(1).atTime(6,0);
        jdbc.update("INSERT dbo.work_schedules(employee_id,work_date,planned_start,planned_end,planned_break_minutes) VALUES(?,?,?,?,?)",employee,workDate,start,end,60);
        long request=id(requests.leave(new Leave(type,start,end,"Ca qua đêm"),worker));
        requests.approve(request,new Decision(null,null),admin);hours("7");
        assertEquals(limit,((Number)((Map<?,?>)((List<?>)requests.detail(request,worker).get("allocations")).get(0)).get("entitlementId")).longValue());
    });}
    @Test void startedLeaveCannotBeApprovedThroughOrdinaryWorkflow(){rollback(()->{
        long request=id(requests.leave(leave(1),worker));
        now.set(day.plusDays(1).atTime(8,1).atZone(ZoneId.of("Asia/Ho_Chi_Minh")).toInstant());
        fails(400,()->requests.approve(request,new Decision(null,null),admin));hours("0");
    });}
    @Test void scheduleChangesWhilePendingAreRecomputedAtApproval(){rollback(()->{
        long request=id(requests.leave(leave(1),worker));
        jdbc.update("UPDATE dbo.work_schedules SET planned_break_minutes=60 WHERE employee_id=? AND work_date=?",employee,day.plusDays(1));
        requests.approve(request,new Decision(null,null),admin);hours("8");
    });}
    @Test void approvalRevalidatesBalanceAfterOtherApproval(){rollback(()->{
        long a=id(requests.leave(leave(1),worker)),b=id(requests.leave(leave(2),worker));
        requests.approve(a,new Decision(null,null),admin);fails(409,()->requests.approve(b,new Decision(null,null),admin));hours("9");
    });}
    @Test void approvalsFreezeSchedules(){rollback(()->{
        long request=id(requests.leave(leave(1),worker));requests.approve(request,new Decision(null,null),admin);
        assertThrows(org.springframework.dao.DataAccessException.class,()->jdbc.update("UPDATE dbo.work_schedules SET planned_end=DATEADD(HOUR,1,planned_end) WHERE employee_id=? AND work_date=?",employee,day.plusDays(1)));
    });}
    @Test void leaveAuditFailureRollsBackHeaderAndSubtype(){
        PersonnelWorkflowSupport target=org.springframework.test.util.AopTestUtils.getUltimateTargetObject(support);
        doThrow(new IllegalStateException("audit unavailable")).when(target).audit(any(),eq("LEAVE_REQUEST_CREATE"),anyString(),anyLong(),any(),any(),any());
        rollback(()->{
            assertThrows(IllegalStateException.class,()->requests.leave(leave(1),worker));
        });
        // A separate transaction verifies no request escaped the failed transaction.
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM dbo.employee_requests WHERE employee_id=?",Integer.class,employee));
    }
    @Test void ownQueueAndManagerQueueHaveValidatedPaging(){rollback(()->{
        requests.leave(leave(1),worker);assertEquals(1,requests.list(true,null,null,0,10,worker).getTotalElements());
        assertTrue(requests.list(false,"CHO_DUYET","NGHI_PHEP",0,100,admin).getTotalElements()>=1);
        fails(400,()->requests.list(true,"BOGUS",null,0,10,worker));fails(400,()->requests.list(true,null,null,-1,10,worker));
    });}
    @Test void resignationStaysWorkingUntilDayAfterLastDateAndRunsOnce(){rollback(()->{
        LocalDate last=day.plusDays(3);long request=id(requests.resign(new Resignation(last,"Leaving"),worker));
        fails(409,()->requests.resign(new Resignation(last,"duplicate"),worker));
        requests.approve(request,new Decision(last,"accepted"),admin);
        assertEquals("DANG_LAM",jdbc.queryForObject("SELECT employment_status FROM dbo.employees WHERE employee_id=?",String.class,employee));
        fails(409,()->requests.execute(request,adminId));
        now.set(last.atTime(23,59).atZone(ZoneId.of("Asia/Ho_Chi_Minh")).toInstant());
        fails(409,()->requests.execute(request,adminId));
        now.set(last.plusDays(1).atStartOfDay(ZoneId.of("Asia/Ho_Chi_Minh")).toInstant());
        requests.execute(request,adminId);requests.execute(request,adminId);
        assertEquals("DA_NGHI",jdbc.queryForObject("SELECT employment_status FROM dbo.employees WHERE employee_id=?",String.class,employee));
        assertFalse(jdbc.queryForObject("SELECT is_active FROM dbo.accounts WHERE username=?",Boolean.class,worker.getName()));
        assertEquals(last,jdbc.queryForObject("SELECT termination_date FROM dbo.employees WHERE employee_id=?",LocalDate.class,employee));
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM dbo.g2_resignation_executions WHERE request_id=?",Integer.class,request));
    });}
    @Test void resignationRejectsPastDateAndScheduleBeyondLastDay(){rollback(()->{
        fails(400,()->requests.resign(new Resignation(day.minusDays(1),"past"),worker));
        long request=id(requests.resign(new Resignation(day,"today"),worker));fails(409,()->requests.approve(request,new Decision(null,null),admin));
    });}
    @Test void rejectedResignationAllowsNewSubmission(){rollback(()->{
        long a=id(requests.resign(new Resignation(day.plusDays(4),"first"),worker));requests.reject(a,new Rejection("revise"),admin);
        assertNotNull(requests.resign(new Resignation(day.plusDays(5),"second"),worker));
    });}
    @Test void resignationCannotBeApprovedWhileEmployeeIsHead(){rollback(()->{
        long head=jdbc.queryForObject("SELECT position_id FROM dbo.positions WHERE position_code='TRUONG_PHONG'",Long.class);
        jdbc.update("UPDATE dbo.employee_assignments SET position_id=? WHERE employee_id=?",head,employee);
        long request=id(requests.resign(new Resignation(day.plusDays(5),"head leaving"),worker));
        fails(409,()->requests.approve(request,new Decision(null,null),admin));
    });}
    @Test void qualificationCatalogAndSoftDeleteKeepMetadataAndSinglePrimary(){rollback(()->{
        var levelRequest=new QualificationLevelRequestDTO();levelRequest.setLevelCode(tag);levelRequest.setLevelName("G2 level");
        var level=catalogs.write(admin,"qualification_levels",null,"QUALIFICATION_LEVEL_CREATE",()->levels.addQualificationLevel(levelRequest),QualificationLevelDTO::qualificationLevelId);
        var q=new EmployeeQualificationRequestDTO();q.setQualificationLevelId(level.qualificationLevelId());q.setGraduationYear(2020);q.setIsPrimary(true);
        var first=catalogs.qualification(admin,employee,null,"EMPLOYEE_QUALIFICATION_CREATE",()->qualifications.addEmployeeQualification(employee,q),EmployeeQualificationDTO::employeeQualificationId);
        var second=catalogs.qualification(admin,employee,null,"EMPLOYEE_QUALIFICATION_CREATE",()->qualifications.addEmployeeQualification(employee,q),EmployeeQualificationDTO::employeeQualificationId);
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM dbo.employee_qualifications WHERE employee_id=? AND is_primary=1",Integer.class,employee));
        assertFalse(qualifications.getEmployeeQualificationById(first.employeeQualificationId()).isPrimary());
        assertEquals(adminId,jdbc.queryForObject("SELECT updated_by FROM dbo.employee_qualifications WHERE employee_qualification_id=?",Long.class,first.employeeQualificationId()));
        catalogs.write(admin,"employee_qualifications",second.employeeQualificationId(),"EMPLOYEE_QUALIFICATION_DELETE",()->{qualifications.deleteEmployeeQualification(second.employeeQualificationId());return second.employeeQualificationId();},Long::longValue);
        assertEquals(adminId,jdbc.queryForObject("SELECT deleted_by FROM dbo.employee_qualifications WHERE employee_qualification_id=?",Long.class,second.employeeQualificationId()));
        assertEquals(1,qualifications.getQualificationsPage(employee,0,10).getTotalElements());
    });}
    @Test void cannotReduceEntitlementBelowApprovedUsage(){rollback(()->{
        long request=id(requests.leave(leave(2),worker));requests.approve(request,new Decision(null,null),admin);
        var update=new LeaveEntitlementRequestDTO();update.setEmployeeId(employee);update.setLeaveTypeId(type);update.setPeriodStart(day.minusDays(1));update.setPeriodEnd(day.plusMonths(1));update.setEntitledHours(new BigDecimal("1"));
        fails(409,()->catalogs.write(admin,"leave_entitlements",limit,"LEAVE_ENTITLEMENT_UPDATE",()->entitlements.updateLeaveEntitlement(limit,update),LeaveEntitlementDTO::entitlementId));
    });}
}
