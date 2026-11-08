package com.hrm.service;
import com.hrm.model.Employee;
import com.hrm.repository.EmployeeRepository;
import com.hrm.dto.AuditEvent;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.*;
import org.springframework.jdbc.support.JdbcTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import java.nio.file.*;
import java.util.*;
import java.time.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
@EnabledIfSystemProperty(named="hrm.codes.sqlserver-tests",matches="true")
class EmployeePositionCodeIntegrationTest {
 static JdbcTemplate db;static TransactionTemplate tx;static Clock clock=Clock.fixed(Instant.parse("2026-11-08T05:00:00Z"),ZoneId.of("Asia/Bangkok"));
 EmployeeRepository repo;EmployeeCodeService codes;AuditLogService audit;long tv,gv,dept,actor;
 @BeforeAll static void connect()throws Exception{Properties p=new Properties();try(var in=Files.newInputStream(Path.of("src/main/resources/application-local.properties"))){p.load(in);}var ds=new DriverManagerDataSource(p.getProperty("spring.datasource.url"),p.getProperty("spring.datasource.username"),p.getProperty("spring.datasource.password"));db=new JdbcTemplate(ds);tx=new TransactionTemplate(new JdbcTransactionManager(ds));}
 @BeforeEach void setup(){repo=mock(EmployeeRepository.class);audit=new AuditLogService(db,clock);codes=new EmployeeCodeService(db,repo,new AssignmentGuardService(db),audit,clock);tv=db.queryForObject("SELECT position_id FROM dbo.positions WHERE position_code='TV'",Long.class);gv=db.queryForObject("SELECT position_id FROM dbo.positions WHERE position_code='GV'",Long.class);dept=db.queryForObject("SELECT department_id FROM dbo.departments WHERE department_code='TV'",Long.class);actor=db.queryForObject("SELECT account_id FROM dbo.accounts WHERE username='demo.admin'",Long.class);when(repo.saveAndFlush(any())).thenAnswer(call->{Employee e=call.getArgument(0);db.update("UPDATE dbo.employees SET employee_code=? WHERE employee_id=?",e.getEmployeeCode(),e.getEmployeeId());return e;});}
 Employee fixture(){String code="QACODE"+UUID.randomUUID().toString().substring(0,8);db.update("INSERT dbo.employees(employee_code,full_name,hire_date) VALUES(?,N'Kiểm thử cấp mã','2024-01-01')",code);long id=db.queryForObject("SELECT employee_id FROM dbo.employees WHERE employee_code=?",Long.class,code);db.update("INSERT dbo.employee_assignments(employee_id,department_id,position_id,effective_from,created_by) VALUES(?,?,?,'2024-01-01',?)",id,dept,tv,actor);Employee e=new Employee();e.setEmployeeId(id);e.setEmployeeCode(code);when(repo.findByIdForUpdate(id)).thenReturn(Optional.of(e));return e;}
 void move(Employee e,long position,String date){db.update("UPDATE dbo.employee_assignments SET effective_to=? WHERE employee_id=? AND effective_to IS NULL",date,e.getEmployeeId());db.update("INSERT dbo.employee_assignments(employee_id,department_id,position_id,effective_from,created_by) VALUES(?,?,?,?,?)",e.getEmployeeId(),dept,position,LocalDate.parse(date),actor);}
 @Test void allocatesUniquePerPrefixAndRejectsUnmappedPositions(){tx.executeWithoutResult(st->{try{String first=codes.next("TV"),second=codes.next("TV");assertTrue(first.matches("TVV[0-9]{5}"));assertNotEquals(first,second);assertTrue(codes.next("GV").startsWith("GV"));assertThrows(org.springframework.web.server.ResponseStatusException.class,()->codes.next("UNKNOWN"));}finally{st.setRollbackOnly();}});}
 @Test void positionChangeUpdatesCodeAndStructuredAuditAtomically(){tx.executeWithoutResult(st->{try{Employee e=fixture();codes.track(e.getEmployeeId(),tv);String old=e.getEmployeeCode();move(e,gv,"2026-11-08");codes.sync(e,actor,"Đổi chức vụ thử nghiệm");assertTrue(e.getEmployeeCode().matches("GV[0-9]{5}"));assertNotEquals(old,e.getEmployeeCode());assertEquals(1,db.queryForObject("SELECT COUNT(*) FROM dbo.audit_logs WHERE action='EMPLOYEE_POSITION_CODE_CHANGED' AND target_record_key=? AND JSON_VALUE(before_data_json,'$.employeeCode')=? AND JSON_VALUE(after_data_json,'$.employeeCode')=?",Integer.class,e.getEmployeeId().toString(),old,e.getEmployeeCode()));}finally{st.setRollbackOnly();}});}
 @Test void futureTransferWaitsUntilEffectiveDateAndThenActivatesOnce(){tx.executeWithoutResult(st->{try{Employee e=fixture();codes.track(e.getEmployeeId(),tv);String old=e.getEmployeeCode();move(e,gv,"2026-11-09");codes.sync(e,actor,"Điều chuyển tương lai");assertEquals(old,e.getEmployeeCode());var tomorrow=new EmployeeCodeService(db,repo,new AssignmentGuardService(db),audit,Clock.offset(clock,Duration.ofDays(1)));tomorrow.syncDue();String changed=e.getEmployeeCode();assertTrue(changed.startsWith("GV"));tomorrow.syncDue();assertEquals(changed,e.getEmployeeCode());}finally{st.setRollbackOnly();}});}
 @Test void counterRollsBackWhenAuditFails(){long before=db.queryForObject("SELECT last_number FROM dbo.employee_code_counters WHERE prefix='GV'",Long.class);var bad=mock(AuditLogService.class);doThrow(new IllegalStateException("audit failed")).when(bad).record(any(AuditEvent.class));assertThrows(IllegalStateException.class,()->tx.executeWithoutResult(st->{Employee e=fixture();var failing=new EmployeeCodeService(db,repo,new AssignmentGuardService(db),bad,clock);failing.track(e.getEmployeeId(),tv);move(e,gv,"2026-11-08");failing.sync(e,actor,"Test rollback");}));assertEquals(before,db.queryForObject("SELECT last_number FROM dbo.employee_code_counters WHERE prefix='GV'",Long.class));}
}
