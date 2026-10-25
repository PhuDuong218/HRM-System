package com.hrm;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.web.server.ResponseStatusException;

import com.hrm.dto.AttendanceDTOs.AttendanceView;
import com.hrm.service.AttendancePermissionService;
import com.hrm.service.AttendanceService;

/**
 * Task 8 - supplemental Spring AOP integration checks (NO DATABASE ACCESS).
 *
 * These tests exercise the actual Spring @Transactional proxy around
 * unconfirm(),
 * with mock JDBC and a mock transaction manager. They can prove that Spring
 * requests commit/rollback, NOT that SQL Server actually rolls back DML.
 *
 * Future real-SQL fixture, real-SQL forced-audit-error, multi-actor RBAC and
 * two-committed-transactions tests require a separately approved design.
 * Never mistake a mock transaction manager for proof of SQL Server isolation.
 */
class AttendanceUnconfirmExtendedIntegrationTest {
    private static final long RECORD = 12L;
    private static final long ACTOR = 7L;
    private static final long EMPLOYEE = 25L;
    private static final LocalDate DAY = LocalDate.of(2026, 10, 21);

    @Configuration(proxyBeanMethods = false)
    @EnableTransactionManagement(proxyTargetClass = true)
    static class TestConfig {
        @Bean
        JdbcTemplate jdbcTemplate() {
            return mock(JdbcTemplate.class);
        }

        @Bean
        AttendancePermissionService attendancePermissionService() {
            return mock(AttendancePermissionService.class);
        }

        @Bean(name = "scheduleTransactionManager")
        PlatformTransactionManager scheduleTransactionManager() {
            PlatformTransactionManager manager = mock(PlatformTransactionManager.class);
            TransactionStatus status = mock(TransactionStatus.class);
            when(manager.getTransaction(any())).thenReturn(status);
            return manager;
        }

        @Bean
        AttendanceService attendanceService(JdbcTemplate jdbcTemplate,
                AttendancePermissionService permissionService) {
            return new AttendanceService(jdbcTemplate, permissionService);
        }
    }

    private static AttendanceView record(String status, Long confirmedBy) {
        return new AttendanceView(RECORD, EMPLOYEE, 15L, DAY,
                LocalDateTime.of(2026, 10, 21, 8, 0),
                LocalDateTime.of(2026, 10, 21, 17, 0),
                60, "DI_LAM", status, confirmedBy,
                confirmedBy == null ? null : LocalDateTime.of(2026, 10, 21, 18, 0), null);
    }

    private static void mockLockedRecord(JdbcTemplate db, AttendanceView locked) {
        when(db.query(argThat((String sql) -> sql.contains("UPDLOCK")
                && sql.contains("attendance_records")),
                org.mockito.ArgumentMatchers.<RowMapper<AttendanceView>>any(), eq(RECORD)))
                .thenReturn(List.of(locked));
    }

    private static void mockSnapshots(JdbcTemplate db) {
        when(db.queryForObject(argThat((String sql) -> sql.contains("FOR JSON PATH")),
                eq(String.class), eq(RECORD)))
                .thenReturn("{\"verification_status\":\"DA_XAC_NHAN\"}",
                        "{\"verification_status\":\"CHUA_XAC_NHAN\"}");
    }

    @Test
    void springProxyRequestsRollbackWhenAuditInsertFails() {
        try (var ctx = new AnnotationConfigApplicationContext(TestConfig.class)) {
            AttendanceService service = ctx.getBean(AttendanceService.class);
            JdbcTemplate db = ctx.getBean(JdbcTemplate.class);
            AttendancePermissionService permission = ctx.getBean(AttendancePermissionService.class);
            PlatformTransactionManager tx = ctx.getBean("scheduleTransactionManager", PlatformTransactionManager.class);
            TransactionStatus status = tx.getTransaction(null); // mock reference only
            clearInvocations(tx);

            assertTrue(org.springframework.aop.support.AopUtils.isAopProxy(service),
                    "The service must be invoked through the Spring transaction proxy");
            mockLockedRecord(db, record("DA_XAC_NHAN", 99L));
            when(permission.requireAttendanceAccess(eq(ACTOR), eq(EMPLOYEE), eq(DAY),
                    eq(AttendancePermissionService.UNCONFIRM)))
                    .thenReturn(new AttendancePermissionService.Actor(ACTOR, null, "ADMIN"));
            mockSnapshots(db);
            when(db.update(argThat((String sql) -> sql.contains("UPDATE dbo.attendance_records")),
                    eq(ACTOR), eq(RECORD))).thenReturn(1);
            when(db.update(argThat((String sql) -> sql.contains("INSERT INTO dbo.audit_logs")),
                    eq(ACTOR), eq("ATTENDANCE_UNCONFIRM"), eq(Long.toString(RECORD)),
                    eq("Audit failure test"), anyString(), anyString()))
                    .thenThrow(new DataIntegrityViolationException("simulated audit INSERT failure"));

            assertThrows(DataIntegrityViolationException.class,
                    () -> service.unconfirm(RECORD, "Audit failure test", ACTOR));
            verify(db).update(argThat((String sql) -> sql.contains("UPDATE dbo.attendance_records")),
                    eq(ACTOR), eq(RECORD));
            verify(tx).rollback(status);
            verify(tx, never()).commit(any());
        }
    }

    @Test
    void springProxyRequestsRollbackWhenRbacDeniesAccess() {
        try (var ctx = new AnnotationConfigApplicationContext(TestConfig.class)) {
            AttendanceService service = ctx.getBean(AttendanceService.class);
            JdbcTemplate db = ctx.getBean(JdbcTemplate.class);
            AttendancePermissionService permission = ctx.getBean(AttendancePermissionService.class);
            PlatformTransactionManager tx = ctx.getBean("scheduleTransactionManager", PlatformTransactionManager.class);
            TransactionStatus status = tx.getTransaction(null);
            clearInvocations(tx);
            mockLockedRecord(db, record("DA_XAC_NHAN", 99L));
            when(permission.requireAttendanceAccess(eq(ACTOR), eq(EMPLOYEE), eq(DAY),
                    eq(AttendancePermissionService.UNCONFIRM)))
                    .thenThrow(new ResponseStatusException(HttpStatus.FORBIDDEN, "Out of HR scope"));

            ResponseStatusException thrown = assertThrows(ResponseStatusException.class,
                    () -> service.unconfirm(RECORD, "Not authorized", ACTOR));
            assertEquals(HttpStatus.FORBIDDEN, thrown.getStatusCode());
            verify(db, never()).update(anyString(), any(Object[].class));
            verify(tx).rollback(status);
            verify(tx, never()).commit(any());
        }
    }
}
