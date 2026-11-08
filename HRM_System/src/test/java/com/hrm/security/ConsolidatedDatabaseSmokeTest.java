package com.hrm.security;
import com.hrm.dto.LoginRequest;
import com.hrm.service.AuthService;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.web.FilterChainProxy;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Explicitly opt-in: only the new disposable consolidation-verification database. */
@EnabledIfSystemProperty(named="hrm.consolidation.verify",matches="true")
@SpringBootTest(properties={"app.security.jwt-secret=consolidation-verification-demo-key-only-1104",
 "personnel.resignations.scheduler-enabled=false","personnel.resignations.initial-delay-ms=86400000",
 "hrm.contracts.expiry-job-enabled=false","hrm.attendance.missing-checkout-scan-enabled=false"})
class ConsolidatedDatabaseSmokeTest {
 @Autowired JdbcTemplate db; @Autowired AuthService auth; @Autowired WebApplicationContext context; @Autowired FilterChainProxy security;
 @Test void demoUsersAndAllModulesShareConsistentData() throws Exception {
  assertEquals("hrm_center_full_verify_1104",db.queryForObject("SELECT DB_NAME()",String.class));
  var mvc=MockMvcBuilders.webAppContextSetup(context).addFilters(security).build();
  String admin=auth.loginWeb(new LoginRequest("demo.admin","Demo@2026!")).accessToken();
  for(String path:new String[]{"/api/auth/me","/employees/search","/positions","/departments","/contracts","/qualification-levels","/leave-types","/employee-requests","/payroll-periods","/payroll-batches"})
   mvc.perform(get(path).header("Authorization","Bearer "+admin)).andExpect(status().isOk());
  String hr=auth.loginWeb(new LoginRequest("demo.hr","Demo@2026!")).accessToken();
  long inside=db.queryForObject("SELECT employee_id FROM dbo.employees WHERE employee_code='DEMO009'",Long.class);
  long outside=db.queryForObject("SELECT employee_id FROM dbo.employees WHERE employee_code='DEMO011'",Long.class);
  mvc.perform(get("/employees/"+inside+"/detail").header("Authorization","Bearer "+hr)).andExpect(status().isOk());
  mvc.perform(get("/employees/"+outside+"/detail").header("Authorization","Bearer "+hr)).andExpect(status().isForbidden());
  String head=auth.loginWeb(new LoginRequest("demo.head.dt","Demo@2026!")).accessToken();
  mvc.perform(get("/employee-requests/capabilities").header("Authorization","Bearer "+head)).andExpect(status().isOk()).andExpect(jsonPath("$.departmentHead").value(true));
  mvc.perform(get("/employees/"+inside+"/detail").header("Authorization","Bearer "+head)).andExpect(status().isOk());
  assertEquals(0,db.queryForObject("SELECT COUNT(*) FROM sys.foreign_keys WHERE is_disabled=1 OR is_not_trusted=1",Integer.class));
  assertEquals(32,db.queryForObject("SELECT COUNT(*) FROM dbo.employees",Integer.class));
 }
}
