package com.hrm.service;
import com.hrm.dto.EmployeeRehireRequest;
import java.time.*;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import static org.junit.jupiter.api.Assertions.*;
/** SQL Server only, fixture rollback. Never operates on hrm_db. */
@SpringBootTest(properties={"hrm.contracts.expiry-job-enabled=false","personnel.resignations.scheduler-enabled=false"})
@Transactional
class EmployeeRehireSqlServerIntegrationTest {
 @Autowired JdbcTemplate jdbc;
 @Autowired EmployeeRehireService service;
 @jakarta.persistence.PersistenceContext jakarta.persistence.EntityManager em;
 @Test void rehirePersistsANewPeriodWithoutReopeningTheOldPeriodOrAccount(){
 assertEquals("hrm_test",jdbc.queryForObject("SELECT DB_NAME()",String.class));
 String tag="REHIRE-"+UUID.randomUUID().toString().substring(0,12);
 LocalDate day=LocalDate.now(ZoneId.of("Asia/Ho_Chi_Minh"));
 jdbc.update("INSERT dbo.employees(employee_code,full_name,hire_date,employment_status,termination_date) VALUES(?,?,?,'DA_NGHI',?)",tag,"Rehire test",day.minusYears(2),day.minusDays(5));
 long id=jdbc.queryForObject("SELECT employee_id FROM dbo.employees WHERE employee_code=?",Long.class,tag);
 jdbc.update("INSERT dbo.employees(employee_code,full_name,hire_date) VALUES(?,?,?)",tag+"A","Actor test",day.minusYears(1));
 long manager=jdbc.queryForObject("SELECT employee_id FROM dbo.employees WHERE employee_code=?",Long.class,tag+"A");
 jdbc.update("INSERT dbo.accounts(employee_id,role_id,username,password_hash,is_active) SELECT ?,role_id,?,REPLICATE('x',60),1 FROM dbo.roles WHERE role_code='ADMIN'",manager,tag+"A");
 long actor=jdbc.queryForObject("SELECT account_id FROM dbo.accounts WHERE username=?",Long.class,tag+"A");
 jdbc.update("INSERT dbo.accounts(employee_id,role_id,username,password_hash,is_active) SELECT ?,role_id,?,REPLICATE('x',60),0 FROM dbo.roles WHERE role_code='EMPLOYEE'",id,tag);
 jdbc.update("INSERT dbo.departments(department_code,department_name) VALUES(?,?)",tag,"Rehire department");
 long department=jdbc.queryForObject("SELECT department_id FROM dbo.departments WHERE department_code=?",Long.class,tag);
 jdbc.update("INSERT dbo.positions(position_code,position_name) VALUES(?,?)",tag,"Rehire position");
 long position=jdbc.queryForObject("SELECT position_id FROM dbo.positions WHERE position_code=?",Long.class,tag);
 jdbc.update("INSERT dbo.employee_assignments(employee_id,department_id,position_id,effective_from,effective_to) VALUES(?,?,?,?,?)",id,department,position,day.minusYears(2),day.minusDays(4));
 service.rehire(id,new EmployeeRehireRequest(day,department,position,"Tái tuyển kiểm thử"),actor);
 em.flush(); // JPA writes are deferred inside this rollback-only outer test transaction.
 assertEquals("DANG_LAM",jdbc.queryForObject("SELECT employment_status FROM dbo.employees WHERE employee_id=?",String.class,id));
 assertEquals(day.minusYears(2),jdbc.queryForObject("SELECT hire_date FROM dbo.employees WHERE employee_id=?",LocalDate.class,id));
 assertEquals(2,jdbc.queryForObject("SELECT COUNT(*) FROM dbo.employee_assignments WHERE employee_id=?",Integer.class,id));
 assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM dbo.employee_assignments WHERE employee_id=? AND effective_to IS NULL",Integer.class,id));
 assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM dbo.accounts WHERE employee_id=? AND is_active=1",Integer.class,id));
 assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM dbo.audit_logs WHERE target_record_key=? AND action='EMPLOYEE_REHIRE'",Integer.class,Long.toString(id)));
 }
}
