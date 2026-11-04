package com.hrm.service;

import com.hrm.repository.HrDepartmentScopeRepository;
import java.io.FileInputStream;
import java.sql.DriverManager;
import java.util.Properties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import static org.junit.jupiter.api.Assertions.*;

/** Opt-in: the local database is queried only; no scope records are changed. */
@EnabledIfSystemProperty(named = "hrm.scope.readonly", matches = "true")
class HrScopeReadOnlyDiagnosticTest {
    @Test void departmentOptionsMatchTheLocalSchema() throws Exception {
        var properties = new Properties();
        try (var in = new FileInputStream("src/main/resources/application-local.properties")) { properties.load(in); }
        try (var connection = DriverManager.getConnection(properties.getProperty("spring.datasource.url"),
                properties.getProperty("spring.datasource.username"), properties.getProperty("spring.datasource.password"))) {
            connection.setReadOnly(true);
            var db = new JdbcTemplate(new SingleConnectionDataSource(connection, true));
            Long hr = db.queryForObject("SELECT TOP 1 a.account_id FROM dbo.accounts a JOIN dbo.roles r ON r.role_id=a.role_id WHERE r.role_code='HR' ORDER BY a.account_id", Long.class);
            var repository = new HrDepartmentScopeRepository(db);
            assertNotNull(repository.departmentOptions(hr));
            assertNotNull(repository.activeDepartments(hr));
        }
    }
}
