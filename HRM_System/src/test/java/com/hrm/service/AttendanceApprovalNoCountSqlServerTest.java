package com.hrm.service;

import com.hrm.security.AuthenticatedAccount;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.ZoneId;
import java.util.Properties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.support.JdbcTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import static org.junit.jupiter.api.Assertions.*;

@EnabledIfSystemProperty(named="hrm.attendance.approval-smoke", matches="true")
class AttendanceApprovalNoCountSqlServerTest {
    @Test
    void approveMissingHoursWithNoCountAndRollBack() throws Exception {
        var properties = new Properties();
        try (var reader = Files.newBufferedReader(Path.of("src/main/resources/application-local.properties"))) {
            properties.load(reader);
        }
        var ds = new DriverManagerDataSource(properties.getProperty("spring.datasource.url"),
            properties.getProperty("spring.datasource.username"), properties.getProperty("spring.datasource.password"));
        var db = new JdbcTemplate(ds);
        var permissions = new AttendancePermissionService(db);
        var attendance = new AttendanceService(db, permissions);
        var clock = Clock.system(ZoneId.of("Asia/Ho_Chi_Minh"));
        var portal = new AttendancePortalService(db, permissions, attendance,
            new AttendanceSelfClockService(db, clock), clock, 7, 3);
        var admin = db.queryForObject("SELECT account_id,employee_id FROM dbo.accounts WHERE username='demo.admin'",
            (rs, row) -> new AuthenticatedAccount(rs.getLong(1), rs.getObject(2,Long.class), "demo.admin", "ADMIN"));
        Long request = db.queryForObject("""
            SELECT TOP(1) request_id FROM dbo.attendance_portal_requests
            WHERE status='DANG_XU_LY' AND request_type='MISSING_ATTENDANCE' AND received_by=?
            ORDER BY request_id DESC
            """, Long.class, admin.accountId());
        new TransactionTemplate(new JdbcTransactionManager(ds)).executeWithoutResult(tx -> {
            try {
                db.execute("SET NOCOUNT ON");
                portal.decide(admin, request, new AttendancePortalService.Decision(true, null, null));
                var detail = portal.request(admin, false, request);
                assertEquals("DA_CHAP_NHAN", detail.request().status());
                assertEquals("DA_XAC_NHAN", detail.current().row().verificationStatus());
                assertTrue(detail.current().row().checkInReviewed());
                assertTrue(detail.current().row().checkOutReviewed());
            } finally {
                tx.setRollbackOnly();
            }
        });
        assertEquals("DANG_XU_LY", portal.request(admin, false, request).request().status());
    }
}
