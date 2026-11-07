package com.hrm.hrm.service;

import java.time.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import com.hrm.dto.*;
import com.hrm.service.*;
import jakarta.persistence.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.*;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.util.AopTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.json.JsonMapper;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.doAnswer;

/** SQL Server integration tests. All fixtures and mutations are rolled back, never hard-deleted. */
@SpringBootTest
class G1WorkflowIntegrationTest {
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager transactions;
    @Autowired EmployeeCreationAuditService employees;
    @Autowired EmployeeTransferAuditService transfers;
    @Autowired EmployeeTerminationAuditService terminations;
    @Autowired CatalogAuditService catalogs;
    @Autowired DepartmentService departments;
    @Autowired PositionService positions;
    @Autowired EmployeeAssignmentService assignments;
    @Autowired @Qualifier("hrmBusinessClock") Clock clock;
    @MockitoSpyBean EmployeeAuditService audit;
    @PersistenceContext EntityManager em;
    String prefix;
    Long actor, department, position, managerId;
    LocalDate day;
    final JsonMapper json=JsonMapper.builder().build();

    @BeforeEach void onlyTestDatabase() {
        assertEquals("hrm_test",jdbc.queryForObject("SELECT DB_NAME()",String.class).toLowerCase(Locale.ROOT));
    }

    private void fixture() {
        prefix="G1-"+UUID.randomUUID().toString().substring(0,12);
        day=LocalDate.now(clock);
        jdbc.update("INSERT dbo.employees(employee_code,full_name,hire_date) VALUES (?,N'G1 HR',?)",prefix+"HR",day.minusYears(1));
        managerId=jdbc.queryForObject("SELECT employee_id FROM dbo.employees WHERE employee_code=?",Long.class,prefix+"HR");
        jdbc.update("""
                INSERT dbo.accounts(employee_id,role_id,username,password_hash)
                SELECT ?,role_id,?,REPLICATE('x',60) FROM dbo.roles WHERE role_code='ADMIN'
                """,managerId,prefix);
        actor=jdbc.queryForObject("SELECT account_id FROM dbo.accounts WHERE username=?",Long.class,prefix);
        jdbc.update("INSERT dbo.departments(department_code,department_name) VALUES (?,N'G1 nguồn')",prefix);
        department=jdbc.queryForObject("SELECT department_id FROM dbo.departments WHERE department_code=?",Long.class,prefix);
        jdbc.update("INSERT dbo.positions(position_code,position_name) VALUES (?,N'G1 chuyên viên')",prefix);
        position=jdbc.queryForObject("SELECT position_id FROM dbo.positions WHERE position_code=?",Long.class,prefix);
    }

    private void rollback(Runnable body) {
        new TransactionTemplate(transactions).executeWithoutResult(status->{
            status.setRollbackOnly(); fixture(); body.run();
        });
    }

    private EmployeeRequestDTO request(String suffix) {
        var request=new EmployeeRequestDTO();
        request.setEmployeeCode(prefix+suffix); request.setFullName("  Nhân viên G1  ");
        request.setHireDate(day.minusDays(30)); request.setBirthDate(day.minusYears(30));
        request.setDepartmentId(department); request.setPositionId(position); request.setAddress("  Địa chỉ G1  ");
        return request;
    }

    private int audits(String table,Long id,String action) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM dbo.audit_logs WHERE target_table=? AND target_record_key=? AND action=?",
                Integer.class,table,id.toString(),action);
    }

    private ResponseStatusException fails(int status,Runnable body) {
        var error=assertThrows(ResponseStatusException.class,body::run);
        assertEquals(status,error.getStatusCode().value()); return error;
    }

    @Test void creationPersistsActorAssignmentAndJson() { rollback(()->{
        var created=employees.create(request("NEW"),actor);
        assertEquals("Nhân viên G1",created.getFullName());
        assertEquals(actor,jdbc.queryForObject("SELECT created_by FROM dbo.employees WHERE employee_id=?",Long.class,created.getEmployeeId()));
        assertEquals(actor,jdbc.queryForObject("SELECT created_by FROM dbo.employee_assignments WHERE employee_id=?",Long.class,created.getEmployeeId()));
        assertEquals(1,audits("employees",created.getEmployeeId(),"EMPLOYEE_CREATE"));
        String after=jdbc.queryForObject("SELECT after_data_json FROM dbo.audit_logs WHERE target_table='employees' AND target_record_key=? AND action='EMPLOYEE_CREATE'",String.class,created.getEmployeeId().toString());
        assertEquals("Địa chỉ G1",json.readTree(after).get("address").asText());
    }); }

    @Test void putPreservesIdentityAndCanBeUpdatedTwiceWithDatabaseRowVersion() { rollback(()->{
        var created=employees.create(request("NEW"),actor);
        var update=new EmployeeUpdateRequestDTO(); update.setFullName("Tên mới"); update.setEmail("new@example.com");
        employees.update(created.getEmployeeId(),update,actor);
        update.setFullName("Tên lần hai"); employees.update(created.getEmployeeId(),update,actor);
        assertEquals("Tên lần hai",jdbc.queryForObject("SELECT full_name FROM dbo.employees WHERE employee_id=?",String.class,created.getEmployeeId()));
        assertEquals(created.getEmployeeCode(),jdbc.queryForObject("SELECT employee_code FROM dbo.employees WHERE employee_id=?",String.class,created.getEmployeeId()));
        assertEquals("Địa chỉ G1",jdbc.queryForObject("SELECT address FROM dbo.employees WHERE employee_id=?",String.class,created.getEmployeeId()));
        assertEquals(2,audits("employees",created.getEmployeeId(),"EMPLOYEE_PROFILE_UPDATE"));
    }); }

    @Test void transferKeepsContiguousHistoryAndCatalogCanThenBeDeactivated() { rollback(()->{
        var created=employees.create(request("NEW"),actor);
        var d=new DepartmentRequestDTO(); d.setDepartmentCode(prefix+"D2"); d.setDepartmentName("Phòng mới");
        var targetDepartment=catalogs.createDepartment(d,actor);
        var p=new PositionRequestDTO(); p.setPositionCode(prefix+"P2"); p.setPositionName("Chức vụ mới");
        var targetPosition=catalogs.createPosition(p,actor);
        var moved=transfers.transfer(created.getEmployeeId(),new EmployeeTransferRequestDTO(
                targetDepartment.getDepartmentId(),targetPosition.getPositionId(),day),actor);
        assertEquals(day,moved.effectiveFrom());
        var history=assignments.getEmployeeHistory(created.getEmployeeId());
        assertEquals(2,history.size());
        assertEquals(day,history.get(1).effectiveTo());
        assertEquals(actor,jdbc.queryForObject("SELECT created_by FROM dbo.employee_assignments WHERE assignment_id=?",Long.class,moved.assignmentId()));
        assertEquals(1,audits("employees",created.getEmployeeId(),"EMPLOYEE_TRANSFER"));
        catalogs.deactivateDepartment(department,actor); catalogs.deactivatePosition(position,actor);
        assertFalse(departments.getDepartment(department).getIsActive());
        assertFalse(positions.getPositionById(position).getIsActive());
        assertEquals(2,assignments.getEmployeeHistory(created.getEmployeeId()).size());
    }); }

    @Test void cannotDeactivateDepartmentWithCurrentAssignments() { rollback(()->{
        employees.create(request("NEW"),actor); fails(409,()->catalogs.deactivateDepartment(department,actor));
    }); }

    @Test void cannotDeactivatePositionWithCurrentAssignments() { rollback(()->{
        employees.create(request("NEW"),actor); fails(409,()->catalogs.deactivatePosition(position,actor));
    }); }

    @Test void duplicateDepartmentCodeIsConflict() { rollback(()->{
        var d=new DepartmentRequestDTO(); d.setDepartmentCode(prefix); d.setDepartmentName("Trùng mã");
        fails(409,()->catalogs.createDepartment(d,actor));
    }); }

    @Test void duplicatePositionCodeIsConflict() { rollback(()->{
        var p=new PositionRequestDTO(); p.setPositionCode(prefix); p.setPositionName("Trùng mã");
        fails(409,()->catalogs.createPosition(p,actor));
    }); }

    @Test void departmentCodeIsImmutable() { rollback(()->{
        var d=new DepartmentRequestDTO(); d.setDepartmentCode(prefix+"CHANGED"); d.setDepartmentName("Đổi mã");
        fails(400,()->catalogs.updateDepartment(department,d,actor));
    }); }

    @Test void positionCodeIsImmutable() { rollback(()->{
        var p=new PositionRequestDTO(); p.setPositionCode(prefix+"CHANGED"); p.setPositionName("Đổi mã");
        fails(400,()->catalogs.updatePosition(position,p,actor));
    }); }

    @Test void searchFiltersAndPagingWorkForCatalogs() { rollback(()->{
        assertEquals(1,departments.searchDepartments(prefix,true,0,10).getTotalElements());
        assertEquals(1,positions.searchPositions(prefix,true,0,10).getTotalElements());
        assertEquals(0,positions.searchPositions(prefix,false,0,10).getTotalElements());
        fails(400,()->departments.searchDepartments(null,null,-1,10));
    }); }

    @Test void cannotCreateInInactiveDepartment() { rollback(()->{
        catalogs.deactivateDepartment(department,actor);
        fails(409,()->employees.create(request("NEW"),actor));
    }); }

    @Test void cannotCreateWithInactivePosition() { rollback(()->{
        catalogs.deactivatePosition(position,actor);
        fails(409,()->employees.create(request("NEW"),actor));
    }); }

    @Test void cannotCreateWithFutureHireDate() { rollback(()->{
        var r=request("NEW"); r.setHireDate(day.plusDays(1)); fails(400,()->employees.create(r,actor));
    }); }

    @Test void cannotCreateWithBirthDateAfterHireDate() { rollback(()->{
        var r=request("NEW"); r.setBirthDate(day.minusDays(1)); fails(400,()->employees.create(r,actor));
    }); }

    @Test void cannotTransferRetiredEmployee() { rollback(()->{
        var created=employees.create(request("NEW"),actor);
        terminations.terminate(created.getEmployeeId(),actor);
        fails(409,()->transfers.transfer(created.getEmployeeId(),new EmployeeTransferRequestDTO(department,position,day),actor));
    }); }

    @Test void creationAuditFailureRollsBackEmployeeAndAssignment() {
        AtomicReference<Long> createdId=new AtomicReference<>();
        EmployeeAuditService target=AopTestUtils.getUltimateTargetObject(audit);
        doAnswer(call->{
            Long id=call.getArgument(1); createdId.set(id);
            assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM dbo.employee_assignments WHERE employee_id=?",Integer.class,id));
            throw new DataAccessResourceFailureException("G1 audit failure");
        }).when(target).recordCreation(anyLong(),anyLong(),anyMap());
        assertThrows(DataAccessResourceFailureException.class,()->new TransactionTemplate(transactions).executeWithoutResult(status->{
            fixture(); employees.create(request("NEW"),actor); fail("Audit failure must propagate");
        }));
        assertNotNull(createdId.get());
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM dbo.employees WHERE employee_id=?",Integer.class,createdId.get()));
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM dbo.employee_assignments WHERE employee_id=?",Integer.class,createdId.get()));
    }

    @Test void profileAuditFailureRollsBackFlushedUpdate() {
        AtomicReference<Long> updatedId=new AtomicReference<>();
        EmployeeAuditService target=AopTestUtils.getUltimateTargetObject(audit);
        doAnswer(call->{
            Long id=call.getArgument(1); updatedId.set(id);
            assertEquals("Tên mới",jdbc.queryForObject("SELECT full_name FROM dbo.employees WHERE employee_id=?",String.class,id));
            throw new DataAccessResourceFailureException("G1 audit failure");
        }).when(target).recordProfileUpdate(anyLong(),anyLong(),anyMap(),anyMap());
        assertThrows(DataAccessResourceFailureException.class,()->new TransactionTemplate(transactions).executeWithoutResult(status->{
            fixture(); var created=employees.create(request("NEW"),actor);
            var update=new EmployeeUpdateRequestDTO(); update.setFullName("Tên mới");
            employees.update(created.getEmployeeId(),update,actor); fail("Audit failure must propagate");
        }));
        assertNotNull(updatedId.get());
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM dbo.employees WHERE employee_id=?",Integer.class,updatedId.get()));
    }

    @Test void transferAuditFailureRollsBackBothHistoryRows() {
        AtomicReference<Long> movedId=new AtomicReference<>();
        EmployeeAuditService target=AopTestUtils.getUltimateTargetObject(audit);
        doAnswer(call->{
            Long id=call.getArgument(1); movedId.set(id);
            assertEquals(2,jdbc.queryForObject("SELECT COUNT(*) FROM dbo.employee_assignments WHERE employee_id=?",Integer.class,id));
            throw new DataAccessResourceFailureException("G1 audit failure");
        }).when(target).recordTransfer(anyLong(),anyLong(),anyMap(),anyMap());
        assertThrows(DataAccessResourceFailureException.class,()->new TransactionTemplate(transactions).executeWithoutResult(status->{
            fixture(); var created=employees.create(request("NEW"),actor);
            var d=new DepartmentRequestDTO(); d.setDepartmentCode(prefix+"D2"); d.setDepartmentName("Phòng mới");
            var dest=catalogs.createDepartment(d,actor);
            transfers.transfer(created.getEmployeeId(),new EmployeeTransferRequestDTO(dest.getDepartmentId(),position,day),actor);
            fail("Audit failure must propagate");
        }));
        assertNotNull(movedId.get());
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM dbo.employee_assignments WHERE employee_id=?",Integer.class,movedId.get()));
    }
}
