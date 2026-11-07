package com.hrm.hrm.service;

import com.hrm.dto.AuditEvent;
import com.hrm.dto.DirectTerminationRequest;
import com.hrm.security.AuthenticatedAccount;
import com.hrm.service.AuditLogService;
import com.hrm.service.EmployeeRequestService;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** Opt-in live-database probes, restricted to explicitly created QA2 fixtures.
 * No outer test transaction: assertions observe the service proxy's committed/rolled-back result.
 */
@EnabledIfSystemProperty(named="hrm.qa.remaining.enabled", matches="true")
@SpringBootTest(properties={"app.security.jwt-secret=isolated-qa-test-key-1104-at-least-32-characters",
    "spring.jpa.show-sql=false", "personnel.resignations.scheduler-enabled=false",
    "hrm.contracts.expiry-job-enabled=false", "hrm.attendance.missing-checkout-scan-enabled=false",
    "personnel.resignations.initial-delay-ms=86400000"})
class PersonnelRemainingHrDbProbeTest {
    @Autowired JdbcTemplate db;
    @Autowired EmployeeRequestService requests;
    @MockitoSpyBean AuditLogService audit;
    AuthenticatedAccount admin;

    @BeforeEach void guard() {
        assertEquals("hrm_db",db.queryForObject("SELECT DB_NAME()",String.class));
        assertEquals("QA2-FAULT-1104",db.queryForObject("SELECT employee_code FROM dbo.employees WHERE employee_id=45",String.class));
        assertEquals("QA2-EXIT-1104",db.queryForObject("SELECT employee_code FROM dbo.employees WHERE employee_id=44",String.class));
        admin=db.queryForObject("SELECT account_id,employee_id,username FROM dbo.accounts WHERE username='admin.sample' AND is_active=1",
            (r,n)->new AuthenticatedAccount(r.getLong(1),r.getLong(2),r.getString(3),"ADMIN"));
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(admin,null,List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))));
    }
    @AfterEach void clear(){reset(audit);SecurityContextHolder.clearContext();}

    private Map<String,Object> snapshot(long employee) {
        return db.queryForMap("SELECT e.employment_status,e.termination_date,a.is_active,COALESCE(a.token_version,0) token_version FROM dbo.employees e JOIN dbo.accounts a ON a.employee_id=e.employee_id WHERE e.employee_id=?",employee);
    }
    @Test void repeatedExecutionDoesNotTerminateRehiredEmployeeAgain() {
        assertEquals(1,db.queryForObject("SELECT COUNT(*) FROM dbo.g2_resignation_executions x JOIN dbo.employee_requests r ON r.request_id=x.request_id WHERE r.request_id=17 AND r.employee_id=44",Integer.class));
        var before=snapshot(44);
        assertEquals("DANG_LAM",before.get("employment_status"));
        var assignments=db.queryForList("SELECT assignment_id,effective_from,effective_to FROM dbo.employee_assignments WHERE employee_id=44 ORDER BY assignment_id");
        long audits=db.queryForObject("SELECT COUNT_BIG(*) FROM dbo.audit_logs WHERE action='RESIGNATION_EXECUTE' AND target_record_key='17'",Long.class);
        requests.execute(17,admin.accountId());requests.execute(17,admin.accountId());
        assertEquals(before,snapshot(44));
        assertEquals(assignments,db.queryForList("SELECT assignment_id,effective_from,effective_to FROM dbo.employee_assignments WHERE employee_id=44 ORDER BY assignment_id"));
        assertEquals(1,db.queryForObject("SELECT COUNT(*) FROM dbo.g2_resignation_executions WHERE request_id=17",Integer.class));
        assertEquals(audits,db.queryForObject("SELECT COUNT_BIG(*) FROM dbo.audit_logs WHERE action='RESIGNATION_EXECUTE' AND target_record_key='17'",Long.class));
    }
    @Test void lateAuditFailureRollsBackDecisionEmployeeAccountAndAssignment() {
        var before=snapshot(45);assertEquals("DANG_LAM",before.get("employment_status"));assertEquals(Boolean.TRUE,before.get("is_active"));
        var assignments=db.queryForList("SELECT assignment_id,effective_from,effective_to FROM dbo.employee_assignments WHERE employee_id=45 ORDER BY assignment_id");
        long count=db.queryForObject("SELECT COUNT_BIG(*) FROM dbo.employee_requests WHERE employee_id=45",Long.class);
        long auditCount=db.queryForObject("SELECT COUNT_BIG(*) FROM dbo.audit_logs WHERE action IN ('EMPLOYEE_TERMINATION_DECISION','RESIGNATION_EXECUTE')",Long.class);
        var observed=new AtomicBoolean();
        AuditLogService auditTarget=org.springframework.test.util.AopTestUtils.getUltimateTargetObject(audit);
        doAnswer(invocation->{
            AuditEvent event=invocation.getArgument(0);
            if("RESIGNATION_EXECUTE".equals(event.action())) {
                var inside=snapshot(45);
                assertEquals("DA_NGHI",inside.get("employment_status"));assertEquals(Boolean.FALSE,inside.get("is_active"));
                assertEquals(((Number)before.get("token_version")).intValue()+1,((Number)inside.get("token_version")).intValue());
                observed.set(true);
                throw new DataIntegrityViolationException("QA2 injected final audit failure in isolated test context");
            }
            return invocation.callRealMethod();
        }).when(auditTarget).record(any(AuditEvent.class));
        assertThrows(DataIntegrityViolationException.class,()->requests.directTermination(45,
            new DirectTerminationRequest("QA2 rollback probe",LocalDate.of(2026,11,3),DirectTerminationRequest.DecisionType.MUTUAL_AGREEMENT,"QA2-ROLLBACK-1104"),admin));
        assertTrue(observed.get(),"Failure must be injected after employee/account writes, not before them");
        assertEquals(before,snapshot(45));
        assertEquals(assignments,db.queryForList("SELECT assignment_id,effective_from,effective_to FROM dbo.employee_assignments WHERE employee_id=45 ORDER BY assignment_id"));
        assertEquals(count,db.queryForObject("SELECT COUNT_BIG(*) FROM dbo.employee_requests WHERE employee_id=45",Long.class));
        assertEquals(auditCount,db.queryForObject("SELECT COUNT_BIG(*) FROM dbo.audit_logs WHERE action IN ('EMPLOYEE_TERMINATION_DECISION','RESIGNATION_EXECUTE')",Long.class));
    }
}
