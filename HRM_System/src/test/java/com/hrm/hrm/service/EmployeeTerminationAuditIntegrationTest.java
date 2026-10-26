package com.hrm.hrm.service;

import java.time.Clock;
import java.time.LocalDate;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import com.hrm.service.EmployeeAuditService;
import com.hrm.service.EmployeeTerminationAuditService;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
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

@SpringBootTest
class EmployeeTerminationAuditIntegrationTest {

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private EmployeeTerminationAuditService terminationService;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    @Qualifier("hrmBusinessClock")
    private Clock businessClock;

    @PersistenceContext
    private EntityManager entityManager;

    @MockitoSpyBean
    private EmployeeAuditService auditService;

    private Long employeeId;
    private Long managerEmployeeId;
    private Long actorAccountId;
    private Long departmentId;
    private Long positionId;
    private LocalDate day;

    private final JsonMapper mapper = JsonMapper.builder().build();

    @BeforeEach
    void checkDatabase() {
        String database = jdbc.queryForObject(
                "SELECT DB_NAME()", String.class);

        assertEquals(
                "hrm_test",
                database == null ? "" : database.toLowerCase(Locale.ROOT),
                "Chỉ chạy test này trên hrm_test");

        Integer adminCount = jdbc.queryForObject("""
                SELECT COUNT(*) FROM dbo.roles
                WHERE role_code = 'ADMIN'
                """, Integer.class);

        assertNotNull(adminCount);
        assertEquals(1, adminCount.intValue(),
                "Cần có role ADMIN cho tài khoản kiểm thử");
    }

    private Long insertEmployee(String code, String name) {
        jdbc.update("""
                INSERT dbo.employees (
                    employee_code, full_name,
                    hire_date, employment_status
                )
                VALUES (?, ?, ?, 'DANG_LAM')
                """, code, name, day.minusYears(1));

        return jdbc.queryForObject("""
                SELECT employee_id FROM dbo.employees
                WHERE employee_code = ?
                """, Long.class, code);
    }

    // Chỉ gọi bên trong transaction.
    private void createFixture() {
        day = LocalDate.now(businessClock);

        String code = "TEST-TERM-" + UUID.randomUUID()
                .toString().substring(0, 12);

        jdbc.update("""
                INSERT dbo.departments (
                    department_code, department_name, is_active
                )
                VALUES (?, N'Phòng kiểm thử nghỉ việc', 1)
                """, code);

        departmentId = jdbc.queryForObject("""
                SELECT department_id FROM dbo.departments
                WHERE department_code = ?
                """, Long.class, code);

        jdbc.update("""
                INSERT dbo.positions (
                    position_code, position_name, is_active
                )
                VALUES (?, N'Chuyên viên kiểm thử', 1)
                """, code);

        positionId = jdbc.queryForObject("""
                SELECT position_id FROM dbo.positions
                WHERE position_code = ?
                """, Long.class, code);

        employeeId = insertEmployee(
                code, "Nhân viên kiểm thử nghỉ việc");

        managerEmployeeId = insertEmployee(
                code + "-HR", "Người quản lý kiểm thử");

        jdbc.update("""
                INSERT dbo.accounts (
                    employee_id, role_id, username,
                    password_hash, is_active
                )
                SELECT ?, role_id, ?, REPLICATE('x', 60), 1
                FROM dbo.roles
                WHERE role_code = 'ADMIN'
                """, managerEmployeeId, code);

        actorAccountId = jdbc.queryForObject("""
                SELECT account_id FROM dbo.accounts
                WHERE username = ?
                """, Long.class, code);

        jdbc.update("""
                INSERT dbo.employee_assignments (
                    employee_id, department_id,
                    position_id, effective_from
                )
                VALUES (?, ?, ?, ?)
                """,
                employeeId,
                departmentId,
                positionId,
                day.minusDays(30));
    }

    private void inRollbackTransaction(Runnable action) {
        new TransactionTemplate(transactionManager)
                .executeWithoutResult(status -> {
                    status.setRollbackOnly();
                    createFixture();
                    action.run();
                });
    }

    private String employeeStatus() {
        return jdbc.queryForObject("""
                SELECT employment_status FROM dbo.employees
                WHERE employee_id = ?
                """, String.class, employeeId);
    }

    private LocalDate assignmentEnd() {
        return jdbc.queryForObject("""
                SELECT effective_to FROM dbo.employee_assignments
                WHERE employee_id = ?
                """, LocalDate.class, employeeId);
    }

    private int auditCount() {
        Integer count = jdbc.queryForObject("""
                SELECT COUNT(*) FROM dbo.audit_logs
                WHERE target_table = 'employees'
                  AND target_record_key = ?
                  AND action = 'EMPLOYEE_TERMINATE'
                """, Integer.class, employeeId.toString());

        assertNotNull(count);
        return count;
    }

    @Test
    void writesTerminationAssignmentAndCorrectAudit() {
        inRollbackTransaction(() -> {
            terminationService.terminate(employeeId, actorAccountId);

            // Đẩy thay đổi JPA xuống SQL Server trước khi kiểm tra bằng JDBC.
            entityManager.flush();

            assertEquals("DA_NGHI", employeeStatus());
            assertEquals(day.plusDays(1), assignmentEnd());
            assertEquals(1, auditCount());

            LocalDate terminationDate = jdbc.queryForObject("""
                    SELECT termination_date FROM dbo.employees
                    WHERE employee_id = ?
                    """, LocalDate.class, employeeId);

            Long updatedBy = jdbc.queryForObject("""
                    SELECT updated_by FROM dbo.employees
                    WHERE employee_id = ?
                    """, Long.class, employeeId);

            assertEquals(day, terminationDate);
            assertEquals(actorAccountId, updatedBy);

            var row = jdbc.queryForMap("""
                    SELECT actor_account_id,
                           before_data_json,
                           after_data_json
                    FROM dbo.audit_logs
                    WHERE target_table = 'employees'
                      AND target_record_key = ?
                      AND action = 'EMPLOYEE_TERMINATE'
                    """, employeeId.toString());

            assertEquals(
                    actorAccountId.longValue(),
                    ((Number) row.get("actor_account_id")).longValue());

            var before = mapper.readTree(
                    (String) row.get("before_data_json"));

            var after = mapper.readTree(
                    (String) row.get("after_data_json"));

            assertEquals(
                    "DANG_LAM",
                    before.get("employmentStatus").asText());

            assertTrue(before.get("terminationDate").isNull());

            assertEquals(
                    "DA_NGHI",
                    after.get("employmentStatus").asText());

            assertEquals(
                    day.toString(),
                    after.get("terminationDate").asText());
        });
    }

    @Test
    void repeatedTerminationDoesNotCreateAnotherAudit() {
        inRollbackTransaction(() -> {
            terminationService.terminate(employeeId, actorAccountId);
            entityManager.flush();

            assertEquals(1, auditCount());

            ResponseStatusException error = assertThrows(
                    ResponseStatusException.class,
                    () -> terminationService.terminate(
                            employeeId, actorAccountId));

            assertEquals(409, error.getStatusCode().value());
            assertEquals(1, auditCount());
        });
    }

    @Test
    void auditFailureRollsBackEmployeeAssignmentAndAudit() {
        AtomicBoolean sawTerminationInDatabase =
                new AtomicBoolean(false);

        EmployeeAuditService spyTarget =
                AopTestUtils.getUltimateTargetObject(auditService);

        doAnswer(invocation -> {
            entityManager.flush();

            assertEquals("DA_NGHI", employeeStatus());
            assertEquals(day.plusDays(1), assignmentEnd());

            sawTerminationInDatabase.set(true);

            throw new DataAccessResourceFailureException(
                    "Lỗi audit nghỉ việc giả lập");
        }).when(spyTarget).recordTermination(
                anyLong(), anyLong(), anyMap(), anyMap());

        TransactionTemplate transaction =
                new TransactionTemplate(transactionManager);

        assertThrows(
                DataAccessResourceFailureException.class,
                () -> transaction.executeWithoutResult(status -> {
                    createFixture();

                    terminationService.terminate(
                            employeeId, actorAccountId);

                    fail("Lỗi audit phải được truyền ra ngoài");
                }));

        assertTrue(sawTerminationInDatabase.get(),
                "Phải thấy thay đổi trên database trước khi gây lỗi");

        Integer employeeCount = jdbc.queryForObject("""
                SELECT COUNT(*) FROM dbo.employees
                WHERE employee_id = ?
                """, Integer.class, employeeId);

        Integer assignmentCount = jdbc.queryForObject("""
                SELECT COUNT(*) FROM dbo.employee_assignments
                WHERE employee_id = ?
                """, Integer.class, employeeId);

        Integer accountCount = jdbc.queryForObject("""
                SELECT COUNT(*) FROM dbo.accounts
                WHERE account_id = ?
                """, Integer.class, actorAccountId);

        Integer departmentCount = jdbc.queryForObject("""
                SELECT COUNT(*) FROM dbo.departments
                WHERE department_id = ?
                """, Integer.class, departmentId);

        Integer positionCount = jdbc.queryForObject("""
                SELECT COUNT(*) FROM dbo.positions
                WHERE position_id = ?
                """, Integer.class, positionId);

        Integer managerCount = jdbc.queryForObject("""
                SELECT COUNT(*) FROM dbo.employees
                WHERE employee_id = ?
                """, Integer.class, managerEmployeeId);

        assertEquals(Integer.valueOf(0), employeeCount);
        assertEquals(Integer.valueOf(0), assignmentCount);
        assertEquals(Integer.valueOf(0), accountCount);
        assertEquals(Integer.valueOf(0), departmentCount);
        assertEquals(Integer.valueOf(0), positionCount);
        assertEquals(Integer.valueOf(0), managerCount);
        assertEquals(0, auditCount());
    }
}