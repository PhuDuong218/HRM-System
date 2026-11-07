package com.hrm.hrm.service;

import java.io.IOException;
import java.nio.file.*;
import java.security.Principal;
import java.time.*;
import java.util.*;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import com.hrm.HrmApplication;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.filter.OncePerRequestFilter;
import tools.jackson.databind.json.JsonMapper;

/**
 * Explicitly launched HTTP test harness, never part of the application jar or normal test run.
 * Opaque random session tokens are test-only and do not implement or replace the team's JWT module.
 */
@EnabledIfSystemProperty(named="g1.postman",matches="true")
@SpringBootTest(classes=HrmApplication.class,webEnvironment=SpringBootTest.WebEnvironment.DEFINED_PORT,
        properties={"server.address=127.0.0.1","server.port=2006","spring.jpa.show-sql=false"})
@Import(G1PostmanServer.HttpTestConfiguration.class)
public class G1PostmanServer {
    @Autowired Sessions sessions;

    @Test void servePostman() throws Exception {
        Path stop=Path.of("target/g1-postman.stop");
        Files.deleteIfExists(stop);
        Path environment=Path.of("target/g1-postman-runtime.postman_environment.json");
        var values=new ArrayList<Map<String,Object>>();
        for(var entry:Map.of("baseUrl","http://127.0.0.1:2006","managerToken",sessions.managerToken,
                "employeeToken",sessions.employeeToken,"runMutations","false").entrySet()) {
            values.add(Map.of("key",entry.getKey(),"value",entry.getValue(),"enabled",true,"type","default"));
        }
        Files.writeString(environment,JsonMapper.builder().build().writeValueAsString(Map.of(
                "id",UUID.randomUUID().toString(),"name","HRM G1 — local test session",
                "values",values,"_postman_variable_scope","environment")));
        System.out.println("G1_POSTMAN_READY http://127.0.0.1:2006; import "+environment.toAbsolutePath());
        int minutes=Integer.getInteger("g1.postman.minutes",30);
        if(minutes<1 || minutes>120) throw new IllegalArgumentException("Duration must be 1..120 minutes");
        Instant deadline=Instant.now().plusSeconds(minutes*60L);
        try {
            while(Instant.now().isBefore(deadline) && !Files.exists(stop)) Thread.sleep(500);
        } finally { sessions.close(); }
    }

    static final class Sessions implements AutoCloseable {
        final String managerToken=UUID.randomUUID().toString()+UUID.randomUUID();
        final String employeeToken=UUID.randomUUID().toString()+UUID.randomUUID();
        final String managerUsername;
        final String employeeUsername;
        final JdbcTemplate jdbc;
        Sessions(JdbcTemplate jdbc) {
            this.jdbc=jdbc;
            String database=jdbc.queryForObject("SELECT DB_NAME()",String.class);
            if(!"hrm_test".equalsIgnoreCase(database)) throw new IllegalStateException("G1 Postman server requires hrm_test");
            Integer permissions=jdbc.queryForObject("""
                    SELECT COUNT(*) FROM dbo.roles r JOIN dbo.role_permissions rp ON rp.role_id=r.role_id
                    JOIN dbo.permissions p ON p.permission_id=rp.permission_id
                    WHERE r.role_code='ADMIN' AND p.permission_code='EMPLOYEE_MANAGE'
                    """,Integer.class);
            if(permissions==null || permissions==0) throw new IllegalStateException("Missing ADMIN / EMPLOYEE_MANAGE seed permission");
            managerUsername=fixtureAccount("ADMIN");
            employeeUsername=fixtureAccount("EMPLOYEE");
        }

        private String fixtureAccount(String role) {
            String code="G1-POSTMAN-"+role;
            String username="g1.postman."+role.toLowerCase(Locale.ROOT);
            var existing=jdbc.queryForList("SELECT employee_id FROM dbo.employees WHERE employee_code=?",Long.class,code);
            Long employeeId;
            if(existing.isEmpty()) {
                jdbc.update("INSERT dbo.employees(employee_code,full_name,hire_date) VALUES (?,?,?)",code,"G1 Postman test actor "+role,LocalDate.of(2020,1,1));
                employeeId=jdbc.queryForObject("SELECT employee_id FROM dbo.employees WHERE employee_code=?",Long.class,code);
            } else {
                employeeId=existing.get(0);
                String name=jdbc.queryForObject("SELECT full_name FROM dbo.employees WHERE employee_id=?",String.class,employeeId);
                String status=jdbc.queryForObject("SELECT employment_status FROM dbo.employees WHERE employee_id=?",String.class,employeeId);
                if(!("G1 Postman test actor "+role).equals(name) || !"DANG_LAM".equals(status))
                    throw new IllegalStateException("Fixture identity collision: "+code);
            }
            var accounts=jdbc.queryForList("SELECT account_id FROM dbo.accounts WHERE username=?",Long.class,username);
            if(accounts.isEmpty()) {
                int inserted=jdbc.update("""
                        INSERT dbo.accounts(employee_id,role_id,username,password_hash,is_active)
                        SELECT ?,role_id,?,REPLICATE('x',60),1 FROM dbo.roles WHERE role_code=?
                        """,employeeId,username,role);
                if(inserted!=1) throw new IllegalStateException("Missing fixture role "+role);
            } else {
                int updated=jdbc.update("""
                        UPDATE a SET is_active=1 FROM dbo.accounts a JOIN dbo.roles r ON r.role_id=a.role_id
                        WHERE a.username=? AND a.employee_id=? AND r.role_code=? AND a.password_hash=REPLICATE('x',60)
                        """,username,employeeId,role);
                if(updated!=1) throw new IllegalStateException("Fixture account collision: "+username);
            }
            return username;
        }

        @Override public void close() {
            jdbc.update("""
                    UPDATE a SET is_active=0 FROM dbo.accounts a JOIN dbo.employees e ON e.employee_id=a.employee_id
                    WHERE (a.username='g1.postman.admin' AND e.employee_code='G1-POSTMAN-ADMIN')
                       OR (a.username='g1.postman.employee' AND e.employee_code='G1-POSTMAN-EMPLOYEE')
                    """);
        }
    }

    @TestConfiguration(proxyBeanMethods=false)
    static class HttpTestConfiguration {
        @Bean(destroyMethod="close") Sessions g1Sessions(JdbcTemplate jdbc, PlatformTransactionManager transactions) {
            return new TransactionTemplate(transactions).execute(status -> new Sessions(jdbc));
        }

        @Bean FilterRegistrationBean<OncePerRequestFilter> g1TestSessionFilter(Sessions sessions) {
            var registration=new FilterRegistrationBean<OncePerRequestFilter>();
            registration.setOrder(Integer.MIN_VALUE);
            registration.setFilter(new OncePerRequestFilter() {
                @Override protected void doFilterInternal(HttpServletRequest request,HttpServletResponse response,
                        FilterChain chain) throws ServletException,IOException {
                    String path=request.getRequestURI();
                    if(!(path.equals("/health") || path.equals("/api/health") || path.equals("/employee-assignments")
                            || path.equals("/g1/audit") || path.equals("/employees") || path.equals("/employees/search")
                            || path.matches("/employees/[0-9]+(?:/(?:detail|contact|terminate|assignments|transfer))?")
                            || path.equals("/departments") || path.matches("/departments/[0-9]+(?:/head(?:/(?:history|candidates|end))?)?")
                            || path.equals("/positions") || path.matches("/positions/[0-9]+"))) {
                        response.sendError(403,"Only G1 routes are enabled in this test server"); return;
                    }
                    String header=request.getHeader("Authorization");
                    String username=null;
                    if(header!=null) {
                        if(header.equals("Bearer "+sessions.managerToken)) username=sessions.managerUsername;
                        else if(header.equals("Bearer "+sessions.employeeToken)) username=sessions.employeeUsername;
                        else {
                            response.setStatus(401); response.setContentType("application/json;charset=UTF-8");
                            response.getWriter().write("{\"trang_thai\":401,\"loi_chi_tiet\":\"Invalid test session token\"}"); return;
                        }
                    }
                    final String authenticated=username;
                    chain.doFilter(new HttpServletRequestWrapper(request) {
                        @Override public Principal getUserPrincipal() { return authenticated==null?null:()->authenticated; }
                        @Override public String getRemoteUser() { return authenticated; }
                    },response);
                }
            });
            return registration;
        }
    }
}
