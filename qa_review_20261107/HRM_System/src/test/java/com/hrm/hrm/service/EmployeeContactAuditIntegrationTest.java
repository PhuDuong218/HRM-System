package com.hrm.hrm.service;

import java.security.Principal;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import com.hrm.dto.EmployeeContactPatchDTO;
import com.hrm.service.EmployeeAuditService;
import com.hrm.service.EmployeeContactService;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.util.AopTestUtils;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import tools.jackson.databind.json.JsonMapper;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.doAnswer;

@SpringBootTest
class EmployeeContactAuditIntegrationTest {

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private EmployeeContactService contactService;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @MockitoSpyBean
    private EmployeeAuditService auditService;

    private Long employeeId;
    private Long actorAccountId;
    private Principal principal;

    private final JsonMapper mapper = JsonMapper.builder().build();

    @BeforeEach
    void checkDatabase() {
        String database = jdbc.queryForObject(
                "SELECT DB_NAME()", String.class);

        assertEquals(
                "hrm_test",
                database == null ? "" : database.toLowerCase(Locale.ROOT),
                "Chỉ chạy test này trên hrm_test");

        Integer permissionCount = jdbc.queryForObject("""
                SELECT COUNT(*)
                FROM dbo.roles r
                JOIN dbo.role_permissions rp ON rp.role_id = r.role_id
                JOIN dbo.permissions p ON p.permission_id = rp.permission_id
                WHERE r.role_code = 'ADMIN'
                  AND p.permission_code = 'EMPLOYEE_MANAGE'
                """, Integer.class);

        assertNotNull(permissionCount);
        assertTrue(permissionCount > 0,
                "Thiếu quyền EMPLOYEE_MANAGE cho ADMIN");
    }

    // Chỉ gọi phương thức này bên trong transaction của test.
    private void createFixture() {
        String code = "TEST-AUDIT-" + UUID.randomUUID()
                .toString().substring(0, 12);

        jdbc.update("""
                INSERT dbo.employees (
                    employee_code, full_name, hire_date,
                    employment_status, email
                )
                VALUES (
                    ?, N'Nhân viên kiểm thử audit', '2020-01-01',
                    'DANG_LAM', 'old@example.com'
                )
                """, code);

        employeeId = jdbc.queryForObject("""
                SELECT employee_id
                FROM dbo.employees
                WHERE employee_code = ?
                """, Long.class, code);

        jdbc.update("""
                INSERT dbo.accounts (
                    employee_id, role_id, username,
                    password_hash, is_active
                )
                SELECT ?, role_id, ?, REPLICATE('x', 60), 1
                FROM dbo.roles
                WHERE role_code = 'ADMIN'
                """, employeeId, code);

        actorAccountId = jdbc.queryForObject("""
                SELECT account_id
                FROM dbo.accounts
                WHERE username = ?
                """, Long.class, code);

        principal = () -> code;
    }

    private void inRollbackTransaction(Runnable action) {
        new TransactionTemplate(transactionManager)
                .executeWithoutResult(status -> {
                    status.setRollbackOnly();
                    createFixture();
                    action.run();
                });
    }

    private String currentEmail() {
        return jdbc.queryForObject("""
                SELECT email FROM dbo.employees
                WHERE employee_id = ?
                """, String.class, employeeId);
    }

    private int auditCount() {
        Integer count = jdbc.queryForObject("""
                SELECT COUNT(*) FROM dbo.audit_logs
                WHERE target_table = 'employees'
                  AND target_record_key = ?
                  AND action = 'EMPLOYEE_CONTACT_UPDATE'
                """, Integer.class, employeeId.toString());

        assertNotNull(count);
        return count;
    }

    @Test
    void writesContactAndCorrectAuditInSameTransaction() {
        inRollbackTransaction(() -> {
            EmployeeContactPatchDTO request =
                    new EmployeeContactPatchDTO();

            request.setEmail("new@example.com");
            request.setAddress("Nhà \"A\"\nĐường Nguyễn Trãi");

            var result = contactService.patchContact(
                    employeeId, request, principal);

            assertEquals("new@example.com", result.getEmail());
            assertEquals("new@example.com", currentEmail());
            assertEquals(1, auditCount());

            Long updatedBy = jdbc.queryForObject("""
                    SELECT updated_by FROM dbo.employees
                    WHERE employee_id = ?
                    """, Long.class, employeeId);

            assertEquals(actorAccountId, updatedBy);

            var row = jdbc.queryForMap("""
                    SELECT actor_account_id,
                           before_data_json,
                           after_data_json
                    FROM dbo.audit_logs
                    WHERE target_table = 'employees'
                      AND target_record_key = ?
                      AND action = 'EMPLOYEE_CONTACT_UPDATE'
                    """, employeeId.toString());

            assertEquals(
                    actorAccountId.longValue(),
                    ((Number) row.get("actor_account_id")).longValue());

            var before = mapper.readTree(
                    (String) row.get("before_data_json"));

            var after = mapper.readTree(
                    (String) row.get("after_data_json"));

            assertEquals(
                    "old@example.com", before.get("email").asText());

            assertTrue(before.get("address").isNull());

            assertEquals(
                    "new@example.com", after.get("email").asText());

            assertEquals(
                    "Nhà \"A\"\nĐường Nguyễn Trãi",
                    after.get("address").asText());
        });
    }

    @Test
    void unchangedContactDoesNotWriteOrCreateAudit() {
        inRollbackTransaction(() -> {
            EmployeeContactPatchDTO request =
                    new EmployeeContactPatchDTO();

            request.setEmail("old@example.com");

            contactService.patchContact(
                    employeeId, request, principal);

            assertEquals("old@example.com", currentEmail());
            assertEquals(0, auditCount());

            Long updatedBy = jdbc.queryForObject("""
                    SELECT updated_by FROM dbo.employees
                    WHERE employee_id = ?
                    """, Long.class, employeeId);

            assertNull(updatedBy);

            Object updatedAt = jdbc.queryForObject("""
                    SELECT updated_at FROM dbo.employees
                    WHERE employee_id = ?
                    """, Object.class, employeeId);

            assertNull(updatedAt);
        });
    }

    @Test
    void auditFailureRollsBackEntireTransaction() {
        AtomicBoolean sawUpdatedData = new AtomicBoolean(false);

        // Cấu hình spy ở target, tránh gọi qua proxy MANDATORY
        // trong lúc đang thiết lập hành vi giả lập.
        EmployeeAuditService spyTarget =
                AopTestUtils.getUltimateTargetObject(auditService);

        doAnswer(invocation -> {
            assertEquals("new@example.com", currentEmail());
            sawUpdatedData.set(true);

            throw new DataAccessResourceFailureException(
                    "Lỗi audit giả lập để kiểm tra rollback");
        }).when(spyTarget).recordContactUpdate(
                anyLong(), anyLong(), anyMap(), anyMap());

        TransactionTemplate transaction =
                new TransactionTemplate(transactionManager);

        assertThrows(
                DataAccessResourceFailureException.class,
                () -> transaction.executeWithoutResult(status -> {
                    createFixture();

                    EmployeeContactPatchDTO request =
                            new EmployeeContactPatchDTO();

                    request.setEmail("new@example.com");

                    contactService.patchContact(
                            employeeId, request, principal);

                    // Nếu service nuốt lỗi, test thất bại trong transaction
                    // để dữ liệu thử vẫn được rollback.
                    fail("Service phải truyền lỗi audit ra ngoài");
                }));

        assertTrue(sawUpdatedData.get(),
                "Phải thấy dữ liệu mới trước khi gây lỗi audit");

        Integer employeeCount = jdbc.queryForObject("""
                SELECT COUNT(*) FROM dbo.employees
                WHERE employee_id = ?
                """, Integer.class, employeeId);

        Integer accountCount = jdbc.queryForObject("""
                SELECT COUNT(*) FROM dbo.accounts
                WHERE account_id = ?
                """, Integer.class, actorAccountId);

        assertEquals(Integer.valueOf(0), employeeCount);
        assertEquals(Integer.valueOf(0), accountCount);
        assertEquals(0, auditCount());
    }

    @Test
    void auditWriterRequiresAnExistingTransaction() {
        Map<String, String> before =
                Map.of("email", "old@example.com");

        Map<String, String> after =
                Map.of("email", "new@example.com");

        // Không có transaction: phải bị chặn trước khi chạy INSERT.
        assertThrows(
                IllegalTransactionStateException.class,
                () -> auditService.recordContactUpdate(
                        1L, 1L, before, after));
    }
}