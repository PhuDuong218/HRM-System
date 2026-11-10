package com.hrm.service;

import java.nio.file.*;
import java.security.Principal;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@SpringBootTest(properties={"hrm.contracts.expiry-job-enabled=false","personnel.resignations.scheduler-enabled=false","hrm.attendance.missing-checkout-scan-enabled=false","spring.jpa.show-sql=false"})
@Transactional
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class RequestApprovalStagesSqlServerTest {
 @Autowired JdbcTemplate db;@Autowired RequestApprovalStages stages;@Autowired PersonnelWorkflowSupport support;
 @MockitoBean PersonnelAccessService access;
 String tag;long department,otherDepartment,worker,head,headAccount,workerAccount,leaveType;LocalDate today;
 Principal headPrincipal=()->"head",workerPrincipal=()->"worker";
 @BeforeAll void migrate()throws Exception{assertEquals("hrm_test",db.queryForObject("SELECT DB_NAME()",String.class));db.execute(Files.readString(Path.of("database/migrations/005_request_approval_stages.sql")));}
 long employee(String suffix,long dept,long position){String code=tag+suffix;db.update("INSERT dbo.employees(employee_code,full_name,hire_date) VALUES(?,?,?)",code,code,today.minusYears(1));long id=db.queryForObject("SELECT employee_id FROM dbo.employees WHERE employee_code=?",Long.class,code);db.update("INSERT dbo.employee_assignments(employee_id,department_id,position_id,effective_from) VALUES(?,?,?,?)",id,dept,position,today.minusDays(1));return id;}
 long account(long employee,String suffix){String name=tag+suffix;db.update("INSERT dbo.accounts(employee_id,role_id,username,password_hash,is_active) SELECT ?,role_id,?,REPLICATE('x',60),1 FROM dbo.roles WHERE role_code='EMPLOYEE'",employee,name);return db.queryForObject("SELECT account_id FROM dbo.accounts WHERE username=?",Long.class,name);}
 long request(long employee,long account){String code=tag+UUID.randomUUID();db.update("INSERT dbo.employee_requests(employee_id,request_code,request_type,reason,submitted_at,created_by) VALUES(?,?,'NGHI_PHEP','Test',SYSUTCDATETIME(),?)",employee,code,account);long id=db.queryForObject("SELECT request_id FROM dbo.employee_requests WHERE request_code=?",Long.class,code);db.update("INSERT dbo.leave_request_details(request_id,leave_type_id,leave_start,leave_end) VALUES(?,?,?,?)",id,leaveType,today.plusDays(1).atTime(8,0),today.plusDays(1).atTime(17,0));return id;}
 @BeforeEach void fixture(){
  today=support.today();tag="STAGE_"+UUID.randomUUID().toString().substring(0,8);
  db.update("INSERT dbo.leave_types(type_code,type_name,is_paid,is_active) VALUES(?,?,1,1)",tag,tag);leaveType=db.queryForObject("SELECT leave_type_id FROM dbo.leave_types WHERE type_code=?",Long.class,tag);
  db.update("INSERT dbo.departments(department_code,department_name,is_active) VALUES(?,?,1)",tag,tag);department=db.queryForObject("SELECT department_id FROM dbo.departments WHERE department_code=?",Long.class,tag);
  db.update("INSERT dbo.departments(department_code,department_name,is_active) VALUES(?,?,1)",tag+"B",tag+"B");otherDepartment=db.queryForObject("SELECT department_id FROM dbo.departments WHERE department_code=?",Long.class,tag+"B");
  db.update("INSERT dbo.positions(position_code,position_name,is_active) VALUES(?,?,1)",tag,tag);long position=db.queryForObject("SELECT position_id FROM dbo.positions WHERE position_code=?",Long.class,tag);
  long headPosition=db.queryForObject("SELECT position_id FROM dbo.positions WHERE position_code='TRUONG_PHONG'",Long.class);
  worker=employee("W",department,position);head=employee("H",department,headPosition);workerAccount=account(worker,"WA");headAccount=account(head,"HA");
  when(access.actor(headPrincipal)).thenReturn(new PersonnelAccessService.Actor(headAccount,head,"EMPLOYEE"));
  when(access.actor(workerPrincipal)).thenReturn(new PersonnelAccessService.Actor(workerAccount,worker,"EMPLOYEE"));
 }
 @Test void headMustApproveBeforeFinalAndCannotApproveTwice(){long id=request(worker,workerAccount);stages.register(id,worker);assertEquals("PENDING",stages.stage(id).get("stageStatus"));assertEquals(409,assertThrows(ResponseStatusException.class,()->stages.requireFinalApproval(id,worker)).getStatusCode().value());stages.decide(id,true,"Approved",headPrincipal);assertDoesNotThrow(()->stages.requireFinalApproval(id,worker));assertEquals(409,assertThrows(ResponseStatusException.class,()->stages.decide(id,true,"Again",headPrincipal)).getStatusCode().value());}
 @Test void selfApprovalAndMissingRejectionReasonAreBlocked(){long id=request(worker,workerAccount);stages.register(id,worker);assertEquals(403,assertThrows(ResponseStatusException.class,()->stages.decide(id,true,null,workerPrincipal)).getStatusCode().value());assertEquals(400,assertThrows(ResponseStatusException.class,()->stages.decide(id,false,"",headPrincipal)).getStatusCode().value());stages.decide(id,false,"Not suitable",headPrincipal);assertEquals("TU_CHOI",db.queryForObject("SELECT status FROM dbo.employee_requests WHERE request_id=?",String.class,id));}
 @Test void headOwnRequestAndLegacyRequestGoDirectlyToHr(){long id=request(head,headAccount);stages.register(id,head);assertEquals("SKIPPED",stages.stage(id).get("stageStatus"));assertDoesNotThrow(()->stages.requireFinalApproval(id,head));assertDoesNotThrow(()->stages.requireFinalApproval(request(worker,workerAccount),worker));}
 @Test void expiredTenureRemovesAuthorityImmediately(){long id=request(worker,workerAccount);stages.register(id,worker);db.update("UPDATE dbo.employee_assignments SET effective_to=? WHERE employee_id=?",today,head);assertEquals(false,stages.capabilities(headPrincipal).get("departmentHead"));assertEquals(403,assertThrows(ResponseStatusException.class,()->stages.decide(id,true,null,headPrincipal)).getStatusCode().value());}
 @Test void withdrawnRequestCannotBeApproved(){long id=request(worker,workerAccount);stages.register(id,worker);db.update("INSERT dbo.employee_request_withdrawals(request_id,withdrawn_by,withdrawn_at,reason) VALUES(?,?,SYSUTCDATETIME(),'Cancel')",id,workerAccount);assertEquals(409,assertThrows(ResponseStatusException.class,()->stages.decide(id,true,null,headPrincipal)).getStatusCode().value());}
}
