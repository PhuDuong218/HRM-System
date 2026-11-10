package com.hrm.service;

import com.hrm.security.AuthenticatedAccount;
import java.sql.Timestamp;
import java.time.Instant;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.server.ResponseStatusException;
import static org.junit.jupiter.api.Assertions.*;

public class WorkflowRequestScopeTest {
    private JdbcTemplate db;
    private WorkflowRequestQueryService service;
    private final AuthenticatedAccount hrA = new AuthenticatedAccount(1L, 101L, "hr-a", "HR");
    private final AuthenticatedAccount hrB = new AuthenticatedAccount(2L, 102L, "hr-b", "HR");
    private final AuthenticatedAccount admin = new AuthenticatedAccount(3L, 103L, "admin", "ADMIN");

    public static Timestamp utcNow() { return Timestamp.from(Instant.now()); }

    @BeforeEach void setup() {
        JdbcDataSource source = new JdbcDataSource();
        source.setURL("jdbc:h2:mem:workflow_scope;MODE=MSSQLServer;DB_CLOSE_DELAY=-1");
        db = new JdbcTemplate(source) {
            @Override public <T> T queryForObject(String sql, Class<T> type, Object... args) {
                return super.queryForObject(sql.replace("COUNT_BIG(*)", "COUNT(*)"), type, args);
            }
        };
        db.execute("DROP ALL OBJECTS");
        db.execute("CREATE SCHEMA dbo");
        db.execute("CREATE ALIAS SYSUTCDATETIME FOR 'com.hrm.service.WorkflowRequestScopeTest.utcNow'");
        db.execute("CREATE TABLE dbo.roles(role_id bigint, role_code varchar(20))");
        db.execute("CREATE TABLE dbo.accounts(account_id bigint, employee_id bigint, role_id bigint, is_active int)");
        db.execute("CREATE TABLE dbo.employee_assignments(employee_id bigint, department_id bigint, effective_from date, effective_to date)");
        db.execute("CREATE TABLE dbo.hr_department_scopes(hr_account_id bigint, department_id bigint, effective_from timestamp, effective_to timestamp, revoked_at timestamp)");
        db.execute("CREATE TABLE dbo.employee_requests(request_id bigint, employee_id bigint, request_code varchar(20), request_type varchar(20), status varchar(20), reason varchar(20), submitted_at timestamp, created_by bigint, processed_by bigint)");
        db.execute("CREATE TABLE dbo.schedule_change_request_details(request_id bigint, original_start timestamp)");
        db.execute("CREATE TABLE dbo.attendance_records(attendance_id bigint, employee_id bigint, work_date date)");
        db.execute("CREATE TABLE dbo.attendance_correction_request_details(request_id bigint, attendance_id bigint)");
        db.update("INSERT INTO dbo.roles VALUES (1,'HR'),(2,'ADMIN'),(3,'EMPLOYEE')");
        db.update("INSERT INTO dbo.accounts VALUES (1,101,1,1),(2,102,1,1),(3,103,2,1),(4,201,3,1)");
        db.update("INSERT INTO dbo.employee_assignments VALUES (201,10,DATE '2020-01-01',DATE '2025-02-01'),(201,20,DATE '2025-02-01',NULL)");
        db.update("INSERT INTO dbo.hr_department_scopes VALUES (1,10,TIMESTAMP '2020-01-01 00:00:00',NULL,NULL),(2,20,TIMESTAMP '2020-01-01 00:00:00',NULL,NULL)");
        // Both requests were submitted after the employee moved to department B,
        // but refer to work performed in department A.
        db.update("INSERT INTO dbo.employee_requests VALUES (11,201,'S11','DOI_LICH','CHO_DUYET','',TIMESTAMP '2025-02-02 00:00:00',4,NULL),(12,201,'A12','SUA_CONG','CHO_DUYET','',TIMESTAMP '2025-02-02 00:00:00',4,NULL)");
        db.update("INSERT INTO dbo.schedule_change_request_details VALUES (11,TIMESTAMP '2025-01-31 09:00:00')");
        db.update("INSERT INTO dbo.attendance_records VALUES (21,201,DATE '2025-01-31')");
        db.update("INSERT INTO dbo.attendance_correction_request_details VALUES (12,21)");
        service = new WorkflowRequestQueryService(db);
    }

    @Test void transferDoesNotExposeAnotherDepartmentsWorkInListOrDetail() {
        for (var row : new Object[][] {{"DOI_LICH", 11L}, {"SUA_CONG", 12L}}) {
            String type = (String) row[0]; long id = (Long) row[1];
            assertEquals(id, service.get(hrA, type, id).requestId());
            assertThrows(ResponseStatusException.class, () -> service.get(hrB, type, id));
            assertEquals(1, service.list(hrA, type, null, null, 0, 10).totalElements());
            assertEquals(0, service.list(hrB, type, null, null, 0, 10).totalElements());
            assertEquals(id, service.get(admin, type, id).requestId());
        }
    }

    @Test void revokedGrantImmediatelyRemovesAccess() {
        db.update("UPDATE dbo.hr_department_scopes SET revoked_at=CURRENT_TIMESTAMP WHERE hr_account_id=1");
        assertEquals(0, service.list(hrA, "DOI_LICH", null, null, 0, 10).totalElements());
        assertThrows(ResponseStatusException.class, () -> service.get(hrA, "DOI_LICH", 11));
        assertEquals(11, service.get(admin, "DOI_LICH", 11).requestId());
    }
}
