package com.hrm.security;

import com.hrm.HrmApplication;
import com.hrm.service.ContractExpiryJob;
import com.hrm.service.DepartmentService;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

@SpringBootTest(classes = HrmApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {
      "spring.datasource.url=jdbc:h2:mem:jwttransaction;MODE=MSSQLServer;DB_CLOSE_DELAY=-1;INIT=CREATE SCHEMA IF NOT EXISTS dbo",
      "spring.datasource.username=sa", "spring.datasource.password=",
      "spring.jpa.hibernate.ddl-auto=none", "spring.jpa.properties.hibernate.default_schema=PUBLIC",
      "spring.sql.init.mode=never", "debug=false",
      "app.security.jwt-secret=isolated-test-key-with-at-least-32-characters",
      "personnel.resignations.scheduler-enabled=false", "hrm.task.api-enabled=false",
      "hrm.attendance.missing-checkout-scan-enabled=false"
    })
@ActiveProfiles("test")
class JwtDatabaseTransactionIntegrationTest {
    @LocalServerPort int port;
    @Autowired JwtService jwt;
    @Autowired JdbcTemplate db;
    @MockitoBean DepartmentService departments;
    @MockitoBean ContractExpiryJob expiryJob;

    @Test
    void validAdminTokenUsesRealJpaRepositories() throws Exception {
        db.execute("CREATE TABLE roles (role_id BIGINT PRIMARY KEY, role_code VARCHAR(50))");
        db.execute("CREATE TABLE accounts (account_id BIGINT PRIMARY KEY, employee_id BIGINT, role_id BIGINT, username VARCHAR(100), password_hash VARCHAR(255), is_active BOOLEAN, token_version INT, created_at TIMESTAMP, updated_at TIMESTAMP, created_by BIGINT, updated_by BIGINT, row_version VARBINARY(8))");
        db.execute("CREATE TABLE employees (employee_id BIGINT PRIMARY KEY, employment_status VARCHAR(255))");
        db.execute("CREATE TABLE permissions (permission_id BIGINT PRIMARY KEY, permission_code VARCHAR(100))");
        db.execute("CREATE TABLE role_permissions (role_id BIGINT, permission_id BIGINT)");
        db.update("INSERT INTO roles VALUES (1, 'ADMIN')");
        db.update("INSERT INTO accounts (account_id, employee_id, role_id, username, password_hash, is_active, token_version, created_at) VALUES (4, 1, 1, 'admin.test', 'unused', TRUE, 3, CURRENT_TIMESTAMP)");
        db.update("INSERT INTO employees VALUES (1, 'DANG_LAM')");
        db.update("INSERT INTO permissions VALUES (1, 'DEPARTMENT_MANAGE')");
        db.update("INSERT INTO role_permissions VALUES (1, 1)");
        when(departments.getAllDepartment()).thenReturn(List.of());

        String token = jwt.issue(4L, 1L, "admin.test", "ADMIN", 3);
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/departments"))
            .header("Authorization", "Bearer " + token).GET().build();
        HttpResponse<String> response = HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, response.statusCode());
    }
}
