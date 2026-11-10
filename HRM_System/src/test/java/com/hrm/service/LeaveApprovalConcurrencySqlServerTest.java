package com.hrm.service;
import com.hrm.dto.EmployeeRequestDTOs.*;
import java.time.*;
import java.math.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;
import static org.junit.jupiter.api.Assertions.*;

/** Commits synthetic QA_RACE records to let independent SQL transactions observe each other. */
@SpringBootTest(properties={"spring.jpa.show-sql=false","hrm.contracts.expiry-job-enabled=false","personnel.resignations.scheduler-enabled=false","hrm.attendance.missing-checkout-scan-enabled=false"})
class LeaveApprovalConcurrencySqlServerTest {
 @Autowired JdbcTemplate db;@Autowired EmployeeRequestService requests;@Autowired PersonnelWorkflowSupport support;@Autowired PlatformTransactionManager transactions;
 String tag;long employee,admin,type,entitlement;java.security.Principal actor,worker;LocalDate day;
 @BeforeEach void fixture()throws Exception{
  assertEquals("hrm_test",db.queryForObject("SELECT DB_NAME()",String.class));db.execute(java.nio.file.Files.readString(java.nio.file.Path.of("database/migrations/022_leave_request_numbers.sql")));tag="QA_RACE_"+UUID.randomUUID().toString().substring(0,8);day=support.today().plusDays(3);
  new TransactionTemplate(transactions).executeWithoutResult(tx->{
   db.update("INSERT dbo.departments(department_code,department_name) VALUES(?,?)",tag,tag);long dept=db.queryForObject("SELECT department_id FROM dbo.departments WHERE department_code=?",Long.class,tag);
   db.update("INSERT dbo.positions(position_code,position_name) VALUES(?,?)",tag,tag);long position=db.queryForObject("SELECT position_id FROM dbo.positions WHERE position_code=?",Long.class,tag);
   for(String key:List.of("A","E")){db.update("INSERT dbo.employees(employee_code,full_name,hire_date) VALUES(?,?,?)",tag+key,tag+key,day.minusYears(1));long id=db.queryForObject("SELECT employee_id FROM dbo.employees WHERE employee_code=?",Long.class,tag+key);db.update("INSERT dbo.accounts(employee_id,role_id,username,password_hash,is_active) SELECT ?,role_id,?,REPLICATE('x',60),1 FROM dbo.roles WHERE role_code=?",id,tag+key,key.equals("A")?"ADMIN":"EMPLOYEE");if(key.equals("E"))employee=id;}
   admin=db.queryForObject("SELECT account_id FROM dbo.accounts WHERE username=?",Long.class,tag+"A");actor=()->tag+"A";worker=()->tag+"E";
   db.update("INSERT dbo.employee_assignments(employee_id,department_id,position_id,effective_from) VALUES(?,?,?,?)",employee,dept,position,day.minusMonths(1));
   db.update("INSERT dbo.leave_types(type_code,type_name,is_paid) VALUES(?,?,1)",tag,tag);type=db.queryForObject("SELECT leave_type_id FROM dbo.leave_types WHERE type_code=?",Long.class,tag);
   db.update("INSERT dbo.g2_leave_policies(leave_type_id,consumes_entitlement,updated_by) VALUES(?,1,?)",type,admin);
   db.update("INSERT dbo.leave_entitlements(employee_id,leave_type_id,period_start,period_end,entitled_hours) VALUES(?,?,?,?,8)",employee,type,day,day.plusMonths(1));entitlement=db.queryForObject("SELECT entitlement_id FROM dbo.leave_entitlements WHERE employee_id=?",Long.class,employee);
   for(int i=0;i<2;i++)db.update("INSERT dbo.work_schedules(employee_id,work_date,planned_start,planned_end,planned_break_minutes) VALUES(?,?,?,?,0)",employee,day.plusDays(i),day.plusDays(i).atTime(8,0),day.plusDays(i).atTime(16,0));
  });
 }
 @AfterEach void disableSyntheticAccounts(){if(tag!=null)db.update("UPDATE dbo.accounts SET is_active=0,token_version=COALESCE(token_version,0)+1 WHERE username IN (?,?)",tag+"A",tag+"E");}
 long submit(int offset){return ((Number)requests.leave(new Leave(type,day.plusDays(offset).atTime(8,0),day.plusDays(offset).atTime(16,0),"QA concurrency"),worker).get("requestId")).longValue();}
 List<Integer> race(long first,long second)throws Exception{
  ExecutorService pool=Executors.newFixedThreadPool(2);CyclicBarrier barrier=new CyclicBarrier(2);
  try{var tasks=new ArrayList<Future<Integer>>();for(long id:new long[]{first,second})tasks.add(pool.submit(()->{barrier.await(10,TimeUnit.SECONDS);try{requests.approve(id,new Decision(null,"QA concurrent approval"),actor);return 200;}catch(ResponseStatusException e){return e.getStatusCode().value();}}));var results=new ArrayList<Integer>();for(var f:tasks)results.add(f.get(30,TimeUnit.SECONDS));Collections.sort(results);return results;}
  finally{pool.shutdownNow();}
 }
 @Test void simultaneousApprovalCannotSpendTheSameBalanceTwice()throws Exception{long a=submit(0),b=submit(1);assertEquals(List.of(200,409),race(a,b));assertEquals(0,new BigDecimal("8").compareTo(requests.used(entitlement)));}
 @Test void doubleApprovalCreatesExactlyOneAllocation()throws Exception{long a=submit(0);assertEquals(List.of(200,409),race(a,a));assertEquals(1,db.queryForObject("SELECT COUNT(*) FROM dbo.g2_leave_allocations WHERE request_id=?",Integer.class,a));}
}
