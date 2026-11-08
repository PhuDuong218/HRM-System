package com.hrm.service;

import com.hrm.security.AuthenticatedAccount;
import java.io.FileInputStream;
import java.sql.DriverManager;
import java.time.LocalDate;
import java.util.List;
import java.util.Properties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import static org.junit.jupiter.api.Assertions.*;

/** Explicit opt-in, read-only SQL Server syntax/schema check; no real account data is returned. */
@EnabledIfSystemProperty(named="hrm.scoped-reports.readonly",matches="true")
class HrScopedReportSqlServerReadOnlyTest {
    @Test void scopedReportsRunOnSqlServer() throws Exception {
        var properties=new Properties();
        try(var input=new FileInputStream("src/main/resources/application-local.properties")){properties.load(input);}
        try(var connection=DriverManager.getConnection(properties.getProperty("spring.datasource.url"),properties.getProperty("spring.datasource.username"),properties.getProperty("spring.datasource.password"))){
            connection.setReadOnly(true);
            var db=new JdbcTemplate(new SingleConnectionDataSource(connection,true));
            var service=new HrScopedReportService(db,new PersonnelScopeService(db));
            SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(new AuthenticatedAccount(-1L,-1L,"test","HR"),null,List.of()));
            try {
                LocalDate today=LocalDate.now(),from=today.withDayOfMonth(1);
                assertTrue(service.employees(today).byDepartment().isEmpty());
                assertEquals(0,service.contracts(from,today,today,30,null).contractCount());
                assertEquals(0,service.work(from,today,db.queryForObject("SELECT COL_LENGTH('dbo.attendance_records','invalidated_at')",Integer.class)!=null).confirmedWorkDays());
                assertEquals(0,service.payroll(from,today).payrollRecordCount());
            } finally {SecurityContextHolder.clearContext();}
        }
    }
}
