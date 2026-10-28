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
@EnabledIfSystemProperty(named="personnel.postman",matches="true")
@SpringBootTest(classes=HrmApplication.class,webEnvironment=SpringBootTest.WebEnvironment.DEFINED_PORT,
        properties={"server.address=127.0.0.1","server.port=2007","spring.jpa.show-sql=false"})
@Import(PersonnelPostmanServer.HttpTestConfiguration.class)
public class PersonnelPostmanServer {
    @Autowired Sessions sessions;

    @Test void servePostman() throws Exception {
        Path stop=Path.of("target/personnel-postman.stop");
        Files.deleteIfExists(stop);
        Path environment=Path.of("target/personnel-postman-runtime.postman_environment.json");
        var values=new ArrayList<Map<String,Object>>();
        for(var entry:Map.of("baseUrl","http://127.0.0.1:2007","managerToken",sessions.managerToken,
                "employeeToken",sessions.employeeToken,"runMutations","false",
                "employeeId",sessions.employeeId().toString(),"firstDay",LocalDate.now(ZoneId.of("Asia/Ho_Chi_Minh")).plusDays(1).toString()).entrySet()) {
            values.add(Map.of("key",entry.getKey(),"value",entry.getValue(),"enabled",true,"type","default"));
        }
        Files.writeString(environment,JsonMapper.builder().build().writeValueAsString(Map.of(
                "id",UUID.randomUUID().toString(),"name","HRM Personnel — local test session",
                "values",values,"_postman_variable_scope","environment")));
        System.out.println("PERSONNEL_POSTMAN_READY http://127.0.0.1:2007; import "+environment.toAbsolutePath());
        int minutes=Integer.getInteger("personnel.postman.minutes",30);
        if(minutes<1 || minutes>120) throw new IllegalArgumentException("Duration must be 1..120 minutes");
        Instant deadline=Instant.now().plusSeconds(minutes*60L);
        try {
            while(Instant.now().isBefore(deadline) && !Files.exists(stop)) Thread.sleep(500);
        } finally { sessions.close(); }
    }

    static final class Sessions implements AutoCloseable {
        final String managerToken=UUID.randomUUID().toString()+UUID.randomUUID();
        final String employeeToken=UUID.randomUUID().toString()+UUID.randomUUID();
        final String sessionId=UUID.randomUUID().toString().substring(0,8);
        final String managerUsername;
        final String employeeUsername;
        final JdbcTemplate jdbc;
        Sessions(JdbcTemplate jdbc) {
            this.jdbc=jdbc;
            String database=jdbc.queryForObject("SELECT DB_NAME()",String.class);
            if(!"hrm_test".equalsIgnoreCase(database)) throw new IllegalStateException("Personnel Postman server requires hrm_test");
            Integer permissions=jdbc.queryForObject("""
                    SELECT COUNT(*) FROM dbo.roles r JOIN dbo.role_permissions rp ON rp.role_id=r.role_id
                    JOIN dbo.permissions p ON p.permission_id=rp.permission_id
                    WHERE r.role_code='ADMIN' AND p.permission_code='EMPLOYEE_MANAGE'
                    """,Integer.class);
            if(permissions==null || permissions==0) throw new IllegalStateException("Missing ADMIN / EMPLOYEE_MANAGE seed permission");
            managerUsername=fixtureAccount("ADMIN");
            employeeUsername=fixtureAccount("EMPLOYEE");
            Long owner=employeeId();
            String catalogCode="PERSONNEL-PM-"+sessionId;
            jdbc.update("INSERT dbo.departments(department_code,department_name) VALUES(?,N'Personnel Postman fixture')",catalogCode);
            jdbc.update("INSERT dbo.positions(position_code,position_name) VALUES(?,N'Personnel Postman fixture')",catalogCode);
            jdbc.update("""
                INSERT dbo.employee_assignments(employee_id,department_id,position_id,effective_from)
                SELECT ?,d.department_id,p.position_id,? FROM dbo.departments d CROSS JOIN dbo.positions p
                WHERE d.department_code=? AND p.position_code=?
                """,owner,LocalDate.now(ZoneId.of("Asia/Ho_Chi_Minh")).minusDays(30),catalogCode,catalogCode);
            for(int offset=1;offset<=60;offset++) {
                LocalDate date=LocalDate.now(ZoneId.of("Asia/Ho_Chi_Minh")).plusDays(offset);
                jdbc.update("INSERT dbo.work_schedules(employee_id,work_date,planned_start,planned_end,planned_break_minutes) VALUES(?,?,?,?,?)",owner,date,date.atTime(8,0),date.atTime(17,0),60);
            }
        }

        private String fixtureAccount(String role) {
            String code="PERSONNEL-POSTMAN-"+role+"-"+sessionId;
            String username="personnel.postman."+role.toLowerCase(Locale.ROOT)+"."+sessionId;
            var existing=jdbc.queryForList("SELECT employee_id FROM dbo.employees WHERE employee_code=?",Long.class,code);
            Long employeeId;
            if(existing.isEmpty()) {
                jdbc.update("INSERT dbo.employees(employee_code,full_name,hire_date) VALUES (?,?,?)",code,"Personnel Postman test actor "+role,LocalDate.of(2020,1,1));
                employeeId=jdbc.queryForObject("SELECT employee_id FROM dbo.employees WHERE employee_code=?",Long.class,code);
            } else {
                employeeId=existing.get(0);
                String name=jdbc.queryForObject("SELECT full_name FROM dbo.employees WHERE employee_id=?",String.class,employeeId);
                String status=jdbc.queryForObject("SELECT employment_status FROM dbo.employees WHERE employee_id=?",String.class,employeeId);
                if(!("Personnel Postman test actor "+role).equals(name) || !"DANG_LAM".equals(status))
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

        Long employeeId() {return jdbc.queryForObject("SELECT employee_id FROM dbo.accounts WHERE username=?",Long.class,employeeUsername);}
        @Override public void close() {
            if(managerUsername!=null&&employeeUsername!=null) jdbc.update("UPDATE dbo.accounts SET is_active=0 WHERE username IN (?,?)",managerUsername,employeeUsername);
        }
    }
    @TestConfiguration(proxyBeanMethods=false)
    static class HttpTestConfiguration {
        @Bean(destroyMethod="close") Sessions personnelSessions(JdbcTemplate jdbc, PlatformTransactionManager transactions) {
            return new TransactionTemplate(transactions).execute(status -> new Sessions(jdbc));
        }

        @Bean FilterRegistrationBean<OncePerRequestFilter> personnelTestSessionFilter(Sessions sessions) {
            var registration=new FilterRegistrationBean<OncePerRequestFilter>();
            registration.setOrder(Integer.MIN_VALUE);
            registration.setFilter(new OncePerRequestFilter() {
                @Override protected void doFilterInternal(HttpServletRequest request,HttpServletResponse response,
                        FilterChain chain) throws ServletException,IOException {
                    String path=request.getRequestURI();
                    if(!(path.equals("/health") || path.equals("/api/health") || path.matches("/employee-requests(?:/.*)?")
                        || path.matches("/(?:qualification-levels|employee-qualifications|leave-types|leave-entitlements)(?:/.*)?")
                        || path.matches("/employees/[0-9]+/qualifications") || (path.equals("/g2/audit") || path.equals("/personnel/audit") || path.equals("/audit-logs")))) {
                        response.sendError(403,"Only personnel routes are enabled in this test server");return;
                    }                    String header=request.getHeader("Authorization");
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
