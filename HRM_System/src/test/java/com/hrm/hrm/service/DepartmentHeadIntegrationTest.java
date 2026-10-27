package com.hrm.hrm.service;

import java.nio.file.*;
import java.security.Principal;
import java.time.*;
import java.util.UUID;
import com.hrm.dto.*;
import com.hrm.service.*;
import com.hrm.repository.EmployeeRepository;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Requires the existing SQL Server test schema. Every fixture and DDL is rolled back. */
@SpringBootTest
@Transactional
class DepartmentHeadIntegrationTest {
    @Autowired JdbcTemplate jdbc;
    @Autowired AssignmentGuardService guard;
    @Autowired DepartmentHeadAccessService access;
    @Autowired DepartmentService departments;
    @Autowired EmployeeAssignmentService transfers;
    @Autowired EmployeeRepository employees;
    @Autowired EmployeeService employeeService;
    @Autowired CatalogAuditService catalog;
    @jakarta.persistence.PersistenceContext jakarta.persistence.EntityManager entityManager;
    DepartmentHeadService service;
    final LocalDate day=LocalDate.of(2026,10,23);
    Long department,position,otherPosition,employeeA,employeeB,actor;
    Principal principal;
    String prefix;

    @BeforeEach
    void prepare() throws Exception {
        String db=jdbc.queryForObject("SELECT DB_NAME()",String.class);
        assertTrue(db!=null && db.toLowerCase().contains("test"),"Chỉ chạy integration test trên database có tên chứa test");
        jdbc.execute(Files.readString(Path.of("database/migrations/001_department_heads.sql")));
        prefix="TEST-"+UUID.randomUUID().toString().substring(0,12);
        jdbc.update("INSERT dbo.departments(department_code,department_name) VALUES (?,N'Phòng kiểm thử')",prefix);
        department=jdbc.queryForObject("SELECT department_id FROM dbo.departments WHERE department_code=?",Long.class,prefix);
        jdbc.update("INSERT dbo.positions(position_code,position_name) VALUES (?,N'Chuyên viên kiểm thử'),(?,N'Chuyên viên khác')",prefix,prefix+"-2");
        position=jdbc.queryForObject("SELECT position_id FROM dbo.positions WHERE position_code=?",Long.class,prefix);
        otherPosition=jdbc.queryForObject("SELECT position_id FROM dbo.positions WHERE position_code=?",Long.class,prefix+"-2");
        employeeA=employee("A",department); employeeB=employee("B",department);
        Long actorEmployee=employee("MANAGER",department);
        jdbc.update("""
            INSERT dbo.accounts(employee_id,role_id,username,password_hash,is_active)
            SELECT ?,role_id,?,REPLICATE('x',60),1 FROM dbo.roles WHERE role_code='ADMIN'
            """,actorEmployee,prefix);
        actor=jdbc.queryForObject("SELECT account_id FROM dbo.accounts WHERE username=?",Long.class,prefix);
        principal=()->prefix;
        service=new DepartmentHeadService(jdbc,guard,access,
                Clock.fixed(day.atStartOfDay(ZoneId.of("Asia/Ho_Chi_Minh")).toInstant(),ZoneId.of("Asia/Ho_Chi_Minh")));
    }

    private Long employee(String suffix,Long dept) {
        String code=prefix+suffix;
        jdbc.update("INSERT dbo.employees(employee_code,full_name,hire_date) VALUES (?,?,?)",code,"Nhân viên "+suffix,day.minusYears(1));
        Long id=jdbc.queryForObject("SELECT employee_id FROM dbo.employees WHERE employee_code=?",Long.class,code);
        jdbc.update("INSERT dbo.employee_assignments(employee_id,department_id,position_id,effective_from) VALUES (?,?,?,?)",id,dept,position,day.minusDays(10));
        return id;
    }

    private DepartmentHeadDTO appoint(Long employee,LocalDate date,Long next) {
        return service.appoint(department,new DepartmentHeadRequest(employee,date,next,"Bổ nhiệm kiểm thử"),principal);
    }

    private ResponseStatusException fails(int status,Runnable task) {
        var ex=assertThrows(ResponseStatusException.class,task::run);
        assertEquals(status,ex.getStatusCode().value()); return ex;
    }

    @Test void appointmentKeepsContiguousHistoryAndAudit() {
        assertNull(service.getHead(department,day,principal));
        var head=appoint(employeeA,day.minusDays(2),null);
        assertEquals(employeeA,head.employeeId());
        assertNull(service.getHead(department,day.minusDays(3),principal));
        assertEquals(day.minusDays(2),jdbc.queryForObject("SELECT effective_to FROM dbo.employee_assignments WHERE employee_id=? AND position_id=?",LocalDate.class,employeeA,position));
        assertEquals(actor,jdbc.queryForObject("SELECT actor_account_id FROM dbo.audit_logs WHERE target_table='departments' AND target_record_key=?",Long.class,department.toString()));
        assertEquals(employeeA,departments.getDepartment(department).getHeadEmployeeId());
        assertEquals(1,service.history(department,0,10,principal).totalElements());
    }

    @Test void replacementDemotesPreviousHeadAndPreservesBoundary() {
        appoint(employeeA,day.minusDays(2),null);
        appoint(employeeB,day,otherPosition);
        assertEquals(employeeA,service.getHead(department,day.minusDays(1),principal).employeeId());
        assertEquals(employeeB,service.getHead(department,day,principal).employeeId());
        assertEquals(otherPosition,jdbc.queryForObject("SELECT position_id FROM dbo.employee_assignments WHERE employee_id=? AND effective_to IS NULL",Long.class,employeeA));
        assertEquals(2,service.history(department,0,10,principal).totalElements());
        assertEquals(2,jdbc.queryForObject("SELECT COUNT(*) FROM dbo.audit_logs WHERE target_table='departments' AND target_record_key=?",Integer.class,department.toString()));
    }

    @Test void replacementRequiresExplicitNextPositionAndLeavesOldHeadUnchanged() {
        appoint(employeeA,day.minusDays(2),null);
        fails(400,()->appoint(employeeB,day,null));
        assertEquals(employeeA,service.getHead(department,day,principal).employeeId());
        assertEquals(1,service.history(department,0,10,principal).totalElements());
    }

    @Test void endLeavesVacancyAndKeepsHistoricalHead() {
        appoint(employeeA,day.minusDays(2),null);
        service.end(department,new DepartmentHeadEndRequest(day,position,"Kết thúc nhiệm kỳ"),principal);
        assertNull(service.getHead(department,day,principal));
        assertEquals(employeeA,service.getHead(department,day.minusDays(1),principal).employeeId());
        assertEquals(position,jdbc.queryForObject("SELECT position_id FROM dbo.employee_assignments WHERE employee_id=? AND effective_to IS NULL",Long.class,employeeA));
        assertNull(departments.getDepartment(department).getHeadEmployeeId());
    }

    @Test void refusesFutureAndZeroLengthAndDuplicateAppointments() {
        fails(400,()->appoint(employeeA,day.plusDays(1),null));
        fails(400,()->appoint(employeeA,day.minusDays(10),null));
        appoint(employeeA,day.minusDays(2),null);
        fails(409,()->appoint(employeeA,day,null));
    }

    @Test void refusesInactiveDepartmentAndRetiredEmployee() {
        jdbc.update("UPDATE dbo.departments SET is_active=0 WHERE department_id=?",department);
        fails(409,()->appoint(employeeA,day,null));
        jdbc.update("UPDATE dbo.departments SET is_active=1 WHERE department_id=?",department);
        jdbc.update("UPDATE dbo.employees SET employment_status='DA_NGHI',termination_date=? WHERE employee_id=?",day,employeeA);
        fails(409,()->appoint(employeeA,day,null));
    }

    @Test void refusesEmployeeFromAnotherDepartment() {
        jdbc.update("INSERT dbo.departments(department_code,department_name) VALUES (?,N'Phòng khác')",prefix+"-OTHER");
        Long other=jdbc.queryForObject("SELECT department_id FROM dbo.departments WHERE department_code=?",Long.class,prefix+"-OTHER");
        Long outsider=employee("OUT",other);
        fails(409,()->appoint(outsider,day,null));
    }

    @Test void accessUsesAuthenticatedPrincipalAndAccountState() {
        fails(401,()->service.getHead(department,day,null));
        fails(403,()->service.appoint(department,new DepartmentHeadRequest(employeeA,day,null,"x"),()->"missing"));
        jdbc.update("UPDATE dbo.accounts SET is_active=0 WHERE account_id=?",actor);
        fails(403,()->service.getHead(department,day,principal));
    }

    @Test void employeeRoleCannotAppointHead() {
        jdbc.update("UPDATE dbo.accounts SET role_id=(SELECT role_id FROM dbo.roles WHERE role_code='EMPLOYEE') WHERE account_id=?",actor);
        fails(403,()->appoint(employeeA,day,null));
    }

    @Test void genericTransferCannotBypassHeadWorkflow() {
        appoint(employeeA,day.minusDays(2),null);
        fails(409,()->transfers.transferEmployee(employeeA,new EmployeeTransferRequestDTO(department,otherPosition,LocalDate.now())));
    }

    @Test void candidatesAndHistoryArePagedAndExcludeCurrentHead() {
        appoint(employeeA,day.minusDays(2),null);
        var result=service.candidates(department,0,1,principal);
        assertEquals(2,result.totalElements());
        assertEquals(1,result.items().size());
        fails(400,()->service.history(department,-1,10,principal));
    }

    @Test void databaseRejectsHeadOverlapEvenForDirectSql() {
        appoint(employeeA,day.minusDays(2),null);
        Long headPosition=jdbc.queryForObject("SELECT position_id FROM dbo.positions WHERE position_code='TRUONG_PHONG'",Long.class);
        assertThrows(org.springframework.dao.DataAccessException.class,()->jdbc.update(
                "UPDATE dbo.employee_assignments SET position_id=? WHERE employee_id=? AND effective_to IS NULL",headPosition,employeeB));
        // End here: SQL Server may mark the transaction rollback-only after a trigger THROW.
    }

    @Test void migrationCanRunTwice() throws Exception {
        jdbc.execute(Files.readString(Path.of("database/migrations/001_department_heads.sql")));
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM dbo.positions WHERE position_code='TRUONG_PHONG'",Integer.class));
    }

    @Test void ordinaryTransferKeepsHalfOpenBoundary() {
        var result=transfers.transferEmployee(employeeA,new EmployeeTransferRequestDTO(department,otherPosition,day.minusDays(9)));
        assertEquals(day.minusDays(9),result.effectiveFrom());
        assertEquals(day.minusDays(9),jdbc.queryForObject("SELECT effective_to FROM dbo.employee_assignments WHERE employee_id=? AND position_id=?",LocalDate.class,employeeA,position));
    }

    @Test void retirementOnAssignmentStartKeepsTheLastWorkingDay() {
        LocalDate today=LocalDate.now();
        jdbc.update("UPDATE dbo.employee_assignments SET effective_from=? WHERE employee_id=?",today,employeeA);
        employeeService.deleteEmployee(employeeA);
        entityManager.flush();
        assertEquals(today.plusDays(1),jdbc.queryForObject("SELECT effective_to FROM dbo.employee_assignments WHERE employee_id=?",LocalDate.class,employeeA));
        assertEquals("DA_NGHI",jdbc.queryForObject("SELECT employment_status FROM dbo.employees WHERE employee_id=?",String.class,employeeA));
    }

    @Test void departmentAndRetirementCannotSilentlyRemoveAnOpenHead() {
        appoint(employeeA,day.minusDays(2),null);
        fails(409,()->departments.deleteDepartment(department));
        fails(409,()->employeeService.deleteEmployee(employeeA));
    }

    private org.springframework.test.web.servlet.MockMvc mvc() {
        return org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup(
                new com.hrm.controller.DepartmentHeadController(service),
                new com.hrm.controller.DepartmentController(departments,access,catalog))
            .setControllerAdvice(new com.hrm.exception.GlobalExceptionHandler()).build();
    }

    @Test void httpRejectsAnonymousAndClientClaimedRoles() throws Exception {
        mvc().perform(get("/departments/"+department+"/head")
                .header("X-Account-Id",actor).header("X-Role","ADMIN"))
            .andExpect(status().isUnauthorized());
        mvc().perform(get("/departments")).andExpect(status().isUnauthorized());
    }

    @Test void httpExposesVacancyAndValidatedAppointment() throws Exception {
        var mvc=mvc();
        mvc.perform(get("/departments/"+department+"/head").principal(principal)).andExpect(status().isNoContent());
        mvc.perform(put("/departments/"+department+"/head").principal(principal)
                .contentType("application/json").content("{}"))
            .andExpect(status().isBadRequest());
        mvc.perform(put("/departments/"+department+"/head").principal(principal)
                .contentType("application/json")
                .content("{\"employeeId\":"+employeeA+",\"effectiveFrom\":\""+day+"\",\"reason\":\"Test appointment\"}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.employeeId").value(employeeA.intValue()));
        mvc.perform(get("/departments/"+department+"/head/history").principal(principal))
            .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1));
    }
}
