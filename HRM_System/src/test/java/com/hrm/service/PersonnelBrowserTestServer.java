package com.hrm.service;

import java.nio.file.*;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.json.JsonMapper;
import com.hrm.dto.EmployeeRequestDTOs.Leave;
import static org.junit.jupiter.api.Assertions.*;

/** Opt-in real JWT/browser harness. Synthetic records remain in hrm_test for audit; accounts are disabled on exit. */
@EnabledIfSystemProperty(named="personnel.browser",matches="true")
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.DEFINED_PORT,properties={"server.address=127.0.0.1","server.port=2010","spring.profiles.active=local,hrm-test","spring.jpa.show-sql=false","app.security.jwt-secret=TEST_ONLY_PERSONNEL_BROWSER_64_CHARACTERS_SECRET_NOT_FOR_PRODUCTION_2026","hrm.contracts.expiry-job-enabled=false","personnel.resignations.scheduler-enabled=false","hrm.attendance.missing-checkout-scan-enabled=false"})
class PersonnelBrowserTestServer {
 @Autowired JdbcTemplate db;@Autowired PasswordEncoder passwords;@Autowired PlatformTransactionManager transactions;@Autowired EmployeeRequestService requests;
 final String tag="QA_UI_"+UUID.randomUUID().toString().substring(0,8);final String password=UUID.randomUUID()+"aA9!";
 final Map<String,Object> fixture=new LinkedHashMap<>();LocalDate today=LocalDate.now(ZoneId.of("Asia/Ho_Chi_Minh"));
 long insertId(String table,String codeColumn,String idColumn,String code){return db.queryForObject("SELECT "+idColumn+" FROM dbo."+table+" WHERE "+codeColumn+"=?",Long.class,code);}
 long department(String suffix){db.update("INSERT dbo.departments(department_code,department_name,is_active) VALUES(?,?,1)",tag+suffix,"QA UI "+suffix);return insertId("departments","department_code","department_id",tag+suffix);}
 long employee(String key,long dept,long position,String role){
  db.update("INSERT dbo.employees(employee_code,full_name,hire_date) VALUES(?,?,?)",tag+key,"QA UI "+key,today.minusYears(1));long employee=insertId("employees","employee_code","employee_id",tag+key);
  db.update("INSERT dbo.employee_assignments(employee_id,department_id,position_id,effective_from) VALUES(?,?,?,?)",employee,dept,position,today.minusMonths(1));
  db.update("INSERT dbo.accounts(employee_id,role_id,username,password_hash,is_active) SELECT ?,role_id,?,?,1 FROM dbo.roles WHERE role_code=?",employee,tag+key,passwords.encode(password),role);
  fixture.put(key+"Username",tag+key);fixture.put(key+"EmployeeId",employee);return employee;
 }
 @Test void serve()throws Exception{
  assertEquals("hrm_test",db.queryForObject("SELECT DB_NAME()",String.class));
  for(String name:List.of("005_request_approval_stages.sql","006_leave_request_rules.sql","007_qualification_validity_and_rank.sql","008_direct_termination_decisions.sql","009_leave_eligibility_and_accrual.sql","010_leave_cancellation_schedule_guard.sql","011_employee_optional_gender.sql","012_schedule_breaks_for_partial_leave.sql","013_leave_accrual_department_scope.sql"))db.execute(Files.readString(Path.of("database/migrations/"+name)));
  new TransactionTemplate(transactions).executeWithoutResult(tx->{
   long a=department("A"),b=department("B");db.update("INSERT dbo.positions(position_code,position_name,is_active) VALUES(?,?,1)",tag,"QA UI Staff");long position=insertId("positions","position_code","position_id",tag);
   long headPosition=db.queryForObject("SELECT position_id FROM dbo.positions WHERE position_code='TRUONG_PHONG'",Long.class);
   employee("admin",b,position,"ADMIN");employee("hr",b,position,"HR");employee("head",a,headPosition,"EMPLOYEE");long worker=employee("employee",a,position,"EMPLOYEE");employee("outside",b,position,"EMPLOYEE");
   long admin=insertId("accounts","username","account_id",tag+"admin"),hr=insertId("accounts","username","account_id",tag+"hr");
   db.update("INSERT dbo.hr_department_scopes(hr_account_id,department_id,effective_from,created_by) VALUES(?,?,?,?)",hr,a,LocalDateTime.now(ZoneOffset.UTC).minusDays(1),admin);
   db.update("INSERT dbo.leave_types(type_code,type_name,is_paid,is_active) VALUES(?,?,1,1)",tag,"QA UI Leave");long type=insertId("leave_types","type_code","leave_type_id",tag);
   db.update("INSERT dbo.g2_leave_policies(leave_type_id,consumes_entitlement,updated_by) VALUES(?,1,?)",type,admin);
   db.update("INSERT dbo.leave_entitlements(employee_id,leave_type_id,period_start,period_end,entitled_hours) VALUES(?,?,?,?,8)",worker,type,today,today.plusYears(1));
   db.update("INSERT dbo.work_schedules(employee_id,work_date,planned_start,planned_end,planned_break_minutes) VALUES(?,?,?,?,0)",worker,today.plusDays(1),today.plusDays(1).atTime(8,0),today.plusDays(1).atTime(16,0));
   var request=requests.leave(new Leave(type,today.plusDays(1).atTime(8,0),today.plusDays(1).atTime(16,0),"QA UI first-stage review"),()->tag+"employee");fixture.put("requestId",request.get("requestId"));fixture.put("leaveTypeId",type);
  });
  fixture.put("password",password);fixture.put("database","hrm_test");fixture.put("baseUrl","http://127.0.0.1:2010");
  Path stop=Path.of("target/personnel-browser.stop"),output=Path.of("target/personnel-browser-session.json");
  Files.writeString(output,JsonMapper.builder().build().writeValueAsString(fixture));
  System.out.println("PERSONNEL_BROWSER_READY: hrm_test, port 2010; synthetic fixture only.");
  try{Instant deadline=Instant.now().plusSeconds(1800);while(!Files.exists(stop)&&Instant.now().isBefore(deadline))Thread.sleep(250);}
  finally{db.update("UPDATE dbo.accounts SET is_active=0,token_version=COALESCE(token_version,0)+1 WHERE username LIKE ?",tag+"%");}
 }
}
