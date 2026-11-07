package com.hrm;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

/**
 * SQL Server OT metadata and data consistency preflight (strictly read-only).
 * Opt in: HRM_OT_SCHEMA_IT=true. Requires local test SQL Server configuration.
 * No INSERT/UPDATE/DELETE/DDL is executed by this test class.
 */
@EnabledIfEnvironmentVariable(named = "HRM_OT_SCHEMA_IT", matches = "true")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("local")
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:sqlserver://localhost:1433;databaseName=hrm_test;encrypt=false;trustServerCertificate=true",
        "spring.jpa.hibernate.ddl-auto=none",
        "hrm.task.api-enabled=false"
})
class OvertimeSchemaReadOnlyIntegrationTest {
    @Autowired
    JdbcTemplate db;

    private void requireTestDatabase() {
        String name = db.queryForObject("SELECT DB_NAME()", String.class);
        assertEquals("hrm_test", name, "STOP: SQL Server must be hrm_test");
    }

    @Test
    void requiredOvertimeTablesExist() {
        requireTestDatabase();
        for (String name : List.of("employee_requests", "overtime_requests", "overtime_breaks",
                "overtime_request_details", "overtime_overflow_intervals", "audit_logs",
                "hr_department_scopes", "employee_assignments", "payroll_batches", "payroll_periods")) {
            Integer n = db.queryForObject("SELECT COUNT(*) FROM sys.tables WHERE object_id=OBJECT_ID(?)",
                    Integer.class, "dbo." + name);
            assertEquals(1, n, "Missing table: " + name);
        }
    }

    @Test
    void workflowTriggersPresentAndEnabled() {
        requireTestDatabase();
        for (String name : List.of("tr_requests_processing", "tr_ot_request_details_guard",
                "tr_ot_overflow_intervals_guard", "tr_ot_overflow_intervals_no_delete",
                "tr_ot_finalized_actual_guard", "tr_ot_finalized_breaks_guard")) {
            Integer n = db.queryForObject("SELECT COUNT(*) FROM sys.triggers WHERE name=? AND is_disabled=0",
                    Integer.class, name);
            assertEquals(1, n, "Trigger missing/disabled: " + name);
        }
    }

    @Test
    void overtimeForeignKeysAreEnabledAndTrusted() {
        requireTestDatabase();
        Integer n = db.queryForObject("""
                SELECT COUNT(*) FROM sys.foreign_keys
                WHERE parent_object_id IN (OBJECT_ID('dbo.overtime_request_details'),
                    OBJECT_ID('dbo.overtime_overflow_intervals'))
                  AND (is_disabled=1 OR is_not_trusted=1)
                """, Integer.class);
        assertEquals(0, n, "Untrusted or disabled OT FK");
        Integer count = db.queryForObject("""
                SELECT COUNT(*) FROM sys.foreign_keys
                WHERE parent_object_id IN (OBJECT_ID('dbo.overtime_request_details'),
                    OBJECT_ID('dbo.overtime_overflow_intervals'))
                """, Integer.class);
        assertTrue(count >= 5, "Expected at least five FKs for new OT tables");
    }

    @Test
    void overtimeCheckConstraintsAreEnabledAndTrusted() {
        requireTestDatabase();
        Integer invalid = db.queryForObject("""
                SELECT COUNT(*) FROM sys.check_constraints
                WHERE parent_object_id IN (OBJECT_ID('dbo.employee_requests'),
                    OBJECT_ID('dbo.overtime_request_details'), OBJECT_ID('dbo.overtime_overflow_intervals'))
                  AND (is_disabled=1 OR is_not_trusted=1)
                """, Integer.class);
        assertEquals(0, invalid, "An OT-related CHECK is disabled or untrusted");
        Integer typeCheckCount = db.queryForObject("""
                SELECT COUNT(*)
                FROM sys.check_constraints
                WHERE parent_object_id = OBJECT_ID('dbo.employee_requests')
                  AND name = 'CK_requests_type'
                  AND is_disabled = 0
                  AND is_not_trusted = 0
                """, Integer.class);

        assertEquals(
                1,
                typeCheckCount,
                "CK_requests_type must exist, be enabled and trusted");
    }

    @Test
    void existingOverflowDecisionsRespectRequestedBounds() {
        requireTestDatabase();
        Long invalid = db.queryForObject("""
                SELECT COUNT_BIG(*) FROM dbo.overtime_overflow_intervals i
                WHERE (i.approved_start IS NULL AND i.approved_end IS NOT NULL)
                   OR (i.approved_start IS NOT NULL AND i.approved_end IS NULL)
                   OR (i.approved_start IS NOT NULL AND (
                        i.approved_start < i.requested_start OR i.approved_end > i.requested_end
                        OR i.approved_start >= i.approved_end))
                   OR i.requested_start >= i.requested_end
                   OR CAST(i.requested_start AS date) <> i.work_date
                   OR i.requested_end > DATEADD(day,1,CAST(i.work_date AS datetime2))
                """, Long.class);
        assertEquals(0L, invalid, "Invalid existing overflow intervals");
    }

    @Test
    void decidedRequestsHaveCorrectSubtypeAndOwner() {
        requireTestDatabase();
        Long invalid = db.queryForObject("""
                SELECT COUNT_BIG(*) FROM dbo.employee_requests r
                LEFT JOIN dbo.overtime_request_details d ON d.request_id=r.request_id
                LEFT JOIN dbo.overtime_requests ot ON ot.overtime_id=d.overtime_id
                WHERE r.request_type IN ('HUY_OT','OT_VUOT')
                  AND (d.request_id IS NULL OR ot.overtime_id IS NULL OR r.employee_id<>ot.employee_id)
                """, Long.class);
        assertEquals(0L, invalid, "OT request/detail parent mismatch");
    }
}
