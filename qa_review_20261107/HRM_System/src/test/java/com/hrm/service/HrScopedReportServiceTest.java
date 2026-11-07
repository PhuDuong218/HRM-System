package com.hrm.service;

import com.hrm.security.AuthenticatedAccount;
import java.sql.Timestamp;
import java.time.*;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

public class HrScopedReportServiceTest {
    private JdbcTemplate db;
    private PersonnelScopeService scope;
    private HrScopedReportService reports;
    public static Timestamp utcNow() { return Timestamp.from(Instant.now()); }

    @BeforeEach void setup() {
        JdbcDataSource source=new JdbcDataSource();
        source.setURL("jdbc:h2:mem:hr_reports;MODE=MSSQLServer;DB_CLOSE_DELAY=-1");
        db=new JdbcTemplate(source);
        db.execute("DROP ALL OBJECTS");
        db.execute("CREATE SCHEMA dbo");
        db.execute("CREATE ALIAS SYSUTCDATETIME FOR 'com.hrm.service.HrScopedReportServiceTest.utcNow'");
        db.execute("CREATE TABLE dbo.hr_department_scopes(hr_account_id bigint,department_id bigint,effective_from timestamp,effective_to timestamp,revoked_at timestamp)");
        db.execute("CREATE TABLE dbo.employee_assignments(assignment_id bigint,employee_id bigint,department_id bigint,position_id bigint,effective_from date,effective_to date)");
        db.execute("CREATE TABLE dbo.employees(employee_id bigint,employment_status varchar(30))");
        db.execute("CREATE TABLE dbo.departments(department_id bigint,department_code varchar(30),department_name varchar(50))");
        db.execute("CREATE TABLE dbo.positions(position_id bigint,position_code varchar(30),position_name varchar(50))");
        db.execute("CREATE TABLE dbo.employee_qualifications(employee_id bigint,qualification_level_id bigint,is_primary int)");
        db.execute("CREATE TABLE dbo.qualification_levels(qualification_level_id bigint,level_code varchar(30),level_name varchar(50))");
        db.execute("CREATE TABLE dbo.contracts(contract_id bigint,employee_id bigint,status varchar(20),start_date date,end_date date)");
        db.execute("CREATE TABLE dbo.attendance_records(employee_id bigint,work_date date,attendance_result varchar(20),verification_status varchar(20),confirmed_by bigint,confirmed_at timestamp,invalidated_at timestamp)");
        db.execute("CREATE TABLE dbo.employee_requests(request_id bigint,employee_id bigint,request_type varchar(20),status varchar(20))");
        db.execute("CREATE TABLE dbo.leave_request_details(request_id bigint,leave_start timestamp,leave_end timestamp)");
        db.execute("CREATE TABLE dbo.overtime_requests(overtime_id bigint,employee_id bigint,status varchar(20),confirmed_by bigint,confirmed_at timestamp,actual_start timestamp,actual_end timestamp,actual_break_minutes int)");
        db.execute("CREATE TABLE dbo.payroll_periods(period_id bigint,period_code varchar(20),period_start date,period_end date)");
        db.execute("CREATE TABLE dbo.payroll_batches(batch_id bigint,period_id bigint,version_no int,status varchar(20),locked_by bigint,locked_at timestamp)");
        db.execute("CREATE TABLE dbo.payroll_details(payroll_detail_id bigint,batch_id bigint,employee_id bigint)");
        db.execute("CREATE TABLE dbo.payroll_lines(payroll_detail_id bigint,direction_snapshot varchar(20),component_code_snapshot varchar(20),amount decimal(19,4))");
        db.update("INSERT INTO dbo.hr_department_scopes VALUES (1,10,TIMESTAMP '2020-01-01 00:00:00',NULL,NULL)");
        db.update("INSERT INTO dbo.departments VALUES (10,'A','Dept A'),(20,'B','Dept B')");
        db.update("INSERT INTO dbo.positions VALUES (1,'P','Position')");
        db.update("INSERT INTO dbo.employees VALUES (201,'DANG_LAM'),(202,'DANG_LAM'),(203,'DANG_LAM')");
        db.update("INSERT INTO dbo.employee_assignments VALUES (1,201,10,1,DATE '2024-01-01',NULL),(2,202,20,1,DATE '2024-01-01',NULL),(3,203,10,1,DATE '2024-01-01',DATE '2025-02-01'),(4,203,20,1,DATE '2025-02-01',NULL)");
        scope=mock(PersonnelScopeService.class);
        when(scope.sqlEmployees("e.employee_id")).thenReturn("e.employee_id IN (201)");
        reports=new HrScopedReportService(db,scope);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(new AuthenticatedAccount(1L,101L,"hr","HR"),null,java.util.List.of()));
    }

    @AfterEach void clear(){SecurityContextHolder.clearContext();}

    @Test void employeeAndContractReportsExcludeOtherDepartments() {
        var employee=reports.employees(LocalDate.of(2025,2,1));
        assertEquals(1,employee.byDepartment().size());
        assertEquals("A",employee.byDepartment().get(0).getGroupCode());
        assertEquals(1L,employee.byStatus().get(0).getEmployeeCount());
        db.update("INSERT INTO dbo.contracts VALUES (1,201,'DA_KY',DATE '2024-01-01',NULL),(2,202,'DA_KY',DATE '2024-01-01',NULL)");
        var contract=reports.contracts(LocalDate.of(2025,1,1),LocalDate.of(2025,1,31),LocalDate.of(2025,1,31),30,null);
        assertEquals(1,contract.contractCount());
    }

    @Test void workReportCountsOnlyHoursWithinGrantedDepartment() {
        db.update("INSERT INTO dbo.attendance_records VALUES (201,DATE '2025-01-31','DI_LAM','DA_XAC_NHAN',1,CURRENT_TIMESTAMP,NULL),(202,DATE '2025-01-31','DI_LAM','DA_XAC_NHAN',1,CURRENT_TIMESTAMP,NULL),(203,DATE '2025-02-01','VANG','DA_XAC_NHAN',1,CURRENT_TIMESTAMP,NULL)");
        db.update("INSERT INTO dbo.employee_requests VALUES (1,203,'NGHI_PHEP','DA_DUYET'),(2,202,'NGHI_PHEP','DA_DUYET')");
        db.update("INSERT INTO dbo.leave_request_details VALUES (1,TIMESTAMP '2025-01-31 23:00:00',TIMESTAMP '2025-02-01 01:00:00'),(2,TIMESTAMP '2025-01-31 12:00:00',TIMESTAMP '2025-01-31 14:00:00')");
        db.update("INSERT INTO dbo.overtime_requests VALUES (1,203,'DA_DUYET',1,CURRENT_TIMESTAMP,TIMESTAMP '2025-01-31 23:00:00',TIMESTAMP '2025-02-01 01:00:00',0),(2,202,'DA_DUYET',1,CURRENT_TIMESTAMP,TIMESTAMP '2025-01-31 12:00:00',TIMESTAMP '2025-01-31 14:00:00',0)");
        var result=reports.work(LocalDate.of(2025,1,31),LocalDate.of(2025,2,1),true);
        assertEquals(1,result.confirmedWorkDays()); assertEquals(0,result.confirmedAbsentDays());
        assertEquals(1,result.approvedLeaveRequests()); assertEquals("1.0000",result.approvedLeaveHours().toPlainString());
        assertEquals(1,result.approvedOvertimeRequests()); assertEquals("1.0000",result.approvedOvertimeHours().toPlainString());
    }

    @Test void payrollDoesNotIncludeAnotherDepartmentOrMixedPeriod() {
        db.update("INSERT INTO dbo.payroll_periods VALUES (1,'P',DATE '2025-01-01',DATE '2025-02-02')");
        db.update("INSERT INTO dbo.payroll_batches VALUES (1,1,1,'DA_CHOT',1,CURRENT_TIMESTAMP)");
        db.update("INSERT INTO dbo.payroll_details VALUES (1,1,201),(2,1,202),(3,1,203)");
        db.update("INSERT INTO dbo.payroll_lines VALUES (1,'ADD','LUONG',100),(2,'ADD','LUONG',200),(3,'ADD','LUONG',300)");
        var result=reports.payroll(LocalDate.of(2025,1,1),LocalDate.of(2025,2,1));
        assertEquals(1,result.payrollRecordCount()); assertEquals(0,result.totals().grossPayroll().compareTo(new java.math.BigDecimal("100")));
    }

    @Test void revokingScopeRemovesWorkAndContractResults() {
        db.update("INSERT INTO dbo.contracts VALUES (1,201,'DA_KY',DATE '2024-01-01',NULL)");
        db.update("INSERT INTO dbo.attendance_records VALUES (201,DATE '2025-01-31','DI_LAM','DA_XAC_NHAN',1,CURRENT_TIMESTAMP,NULL)");
        db.update("UPDATE dbo.hr_department_scopes SET revoked_at=CURRENT_TIMESTAMP WHERE hr_account_id=1");
        assertEquals(0,reports.contracts(LocalDate.of(2025,1,1),LocalDate.of(2025,1,31),LocalDate.of(2025,1,31),30,null).contractCount());
        assertEquals(0,reports.work(LocalDate.of(2025,1,31),LocalDate.of(2025,1,31),true).confirmedWorkDays());
    }
}
