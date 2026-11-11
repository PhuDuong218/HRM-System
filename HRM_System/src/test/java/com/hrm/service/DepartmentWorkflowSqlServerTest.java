package com.hrm.service;
import java.nio.file.*;import java.util.*;import org.junit.jupiter.api.*;import org.junit.jupiter.api.condition.EnabledIfSystemProperty;import org.springframework.jdbc.core.JdbcTemplate;import org.springframework.jdbc.datasource.DriverManagerDataSource;import com.hrm.security.AuthenticatedAccount;import static org.junit.jupiter.api.Assertions.*;
@EnabledIfSystemProperty(named="hrm.department.sqlserver-tests",matches="true")
class DepartmentWorkflowSqlServerTest {
 @Test void headCannotDecideAttendanceOrOvertime()throws Exception{
  var properties=new Properties();properties.load(Files.newBufferedReader(Path.of("src/main/resources/application-local.properties")));var db=new JdbcTemplate(new DriverManagerDataSource(properties.getProperty("spring.datasource.url"),properties.getProperty("spring.datasource.username"),properties.getProperty("spring.datasource.password")));
  var a=db.queryForMap("SELECT ac.account_id,ac.employee_id,r.role_code FROM dbo.accounts ac JOIN dbo.roles r ON r.role_id=ac.role_id WHERE ac.username='demo.head.tv' AND ac.is_active=1");var actor=new AuthenticatedAccount(((Number)a.get("account_id")).longValue(),((Number)a.get("employee_id")).longValue(),"demo.head.tv",a.get("role_code").toString());
  var service = new DepartmentReviewService(db, new DepartmentWorkspaceAccess(db), new NotificationPublisher(db, false));
  for (String kind : List.of("ATTENDANCE", "OT")) {
   var error = assertThrows(org.springframework.web.server.ResponseStatusException.class,
       () -> service.decide(actor, kind, 1L, true, "Kiểm thử quyền duyệt", "v1"));
   assertEquals(403, error.getStatusCode().value());
  }
 }
}
