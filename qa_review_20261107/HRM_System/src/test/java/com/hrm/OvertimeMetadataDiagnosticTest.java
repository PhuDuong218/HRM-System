package com.hrm;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("local")
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:sqlserver://localhost:1433;databaseName=hrm_test;encrypt=false;trustServerCertificate=true",
        "spring.jpa.hibernate.ddl-auto=none"
})
class OvertimeMetadataDiagnosticTest {

    @Autowired
    JdbcTemplate db;

    @Test
    void diagnoseConstraintVisibility() {

        String database = db.queryForObject(
                "SELECT DB_NAME()", String.class);

        if (!"hrm_test".equals(database)) {
            throw new IllegalStateException(
                    "Refusing to run outside hrm_test");
        }

        db.query("""
                SELECT
                    DB_NAME() AS database_name,
                    USER_NAME() AS database_user,
                    HAS_PERMS_BY_NAME(
                        'dbo.employee_requests',
                        'OBJECT',
                        'VIEW DEFINITION'
                    ) AS can_view_definition
                """, rs -> {
            System.out.println("DATABASE: "
                    + rs.getString("database_name"));
            System.out.println("USER: "
                    + rs.getString("database_user"));
            System.out.println("VIEW DEFINITION: "
                    + rs.getObject("can_view_definition"));
        });

        db.query("""
                SELECT name, definition
                FROM sys.check_constraints
                WHERE parent_object_id =
                    OBJECT_ID('dbo.employee_requests')
                  AND name = 'CK_requests_type'
                """, rs -> {
            System.out.println("CHECK NAME: "
                    + rs.getString("name"));
            System.out.println("CHECK DEFINITION: "
                    + rs.getString("definition"));
        });
    }
}