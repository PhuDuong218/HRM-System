package com.hrm.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import static org.junit.jupiter.api.Assertions.*;

/** Uses only a private in-memory database; no application data is modified. */
class ReportDepartmentServiceTest {
    @Test void employeeDetailsMatchCurrentGroupsWithoutDuplicatingQualifications() {
        var db = new JdbcTemplate(new DriverManagerDataSource("jdbc:h2:mem:report_employee_details;MODE=MSSQLServer;DB_CLOSE_DELAY=-1", "sa", ""));
        db.execute("CREATE SCHEMA dbo");
        db.execute("CREATE TABLE dbo.employees(employee_id bigint,employee_code varchar(30),full_name varchar(100),employment_status varchar(20),hire_date date)");
        db.execute("CREATE TABLE dbo.departments(department_id bigint,department_code varchar(30),department_name varchar(100))");
        db.execute("CREATE TABLE dbo.positions(position_id bigint,position_code varchar(30),position_name varchar(100))");
        db.execute("CREATE TABLE dbo.employee_assignments(assignment_id bigint,employee_id bigint,department_id bigint,position_id bigint,effective_from date,effective_to date)");
        db.execute("CREATE TABLE dbo.qualification_levels(qualification_level_id bigint,level_code varchar(30),level_name varchar(100))");
        db.execute("CREATE TABLE dbo.employee_qualifications(employee_id bigint,qualification_level_id bigint,is_primary int)");
        db.update("INSERT INTO dbo.employees VALUES(1,'NV01','One','DANG_LAM','2020-01-01'),(2,'NV02','Two','DA_NGHI','2020-01-01'),(3,'NV03','Three','DANG_LAM','2020-01-01')");
        db.update("INSERT INTO dbo.departments VALUES(1,'A','Dept A'),(2,'B','Dept B')");
        db.update("INSERT INTO dbo.positions VALUES(1,'DEV','Developer')");
        db.update("INSERT INTO dbo.employee_assignments VALUES(1,1,1,1,'2020-01-01',NULL),(2,1,2,NULL,'2021-01-01',NULL),(3,2,1,1,'2020-01-01',NULL)");
        db.update("INSERT INTO dbo.qualification_levels VALUES(1,'UNI','University')");
        db.update("INSERT INTO dbo.employee_qualifications VALUES(1,1,1),(1,1,1),(2,1,0)");
        var service = new ReportDepartmentService(new NamedParameterJdbcTemplate(db));
        var result = service.employeeDetails("qualification", 1L, null, null, 0, 10);
        assertEquals(1, result.totalElements());
        assertEquals("Dept B", result.items().get(0).departmentName());
        assertNull(result.items().get(0).positionName());
        assertEquals(0, service.employeeDetails("qualification", 1L, null, 1L, 0, 10).totalElements());
        assertEquals(2, service.employeeDetails("position", null, null, null, 0, 10).totalElements());
        assertEquals(3, service.employeeDetails("department", null, null, null, 0, 10).items().get(0).employeeId());
        var status = service.employeeDetails("status", null, "DANG_LAM", null, 1, 1);
        assertEquals(2, status.totalElements());
        assertEquals(2, status.totalPages());
        assertEquals(3, status.items().get(0).employeeId());
        assertThrows(IllegalArgumentException.class, () -> service.employeeDetails("unknown", null, null, null, 0, 10));
        assertThrows(IllegalArgumentException.class, () -> service.employeeDetails("status", null, "invalid", null, 0, 10));
        assertThrows(IllegalArgumentException.class, () -> service.employeeDetails("qualification", null, null, null, 0, 10));
        assertThrows(IllegalArgumentException.class, () -> service.employeeDetails("position", 1L, null, null, -1, 10));
    }

    @Test void payrollDetailsUseStoredLinesAndKeepTheSelectedVersionAndDepartment() {
        var db = new JdbcTemplate(new DriverManagerDataSource("jdbc:h2:mem:report_payroll_details;MODE=MSSQLServer;DB_CLOSE_DELAY=-1", "sa", ""));
        db.execute("CREATE SCHEMA dbo");
        db.execute("CREATE TABLE dbo.employees(employee_id bigint,employee_code varchar(30),full_name varchar(100))");
        db.execute("CREATE TABLE dbo.payroll_periods(period_id bigint,period_start date,period_end date)");
        db.execute("CREATE TABLE dbo.payroll_batches(batch_id bigint,period_id bigint,status varchar(20),locked_by bigint,locked_at timestamp)");
        db.execute("CREATE TABLE dbo.payroll_details(payroll_detail_id bigint,batch_id bigint,employee_id bigint)");
        db.execute("CREATE TABLE dbo.payroll_lines(payroll_line_id bigint,payroll_detail_id bigint,line_no int,component_code_snapshot varchar(30),component_name_snapshot varchar(100),direction_snapshot varchar(10),amount decimal(20,4),explanation varchar(100))");
        db.execute("CREATE TABLE dbo.employee_assignments(employee_id bigint,department_id bigint,effective_from date,effective_to date)");
        db.update("INSERT INTO dbo.employees VALUES(1,'NV01','One'),(2,'NV02','Two'),(3,'NV03','Last day transfer')");
        db.update("INSERT INTO dbo.payroll_periods VALUES(1,'2026-11-01','2026-11-30')");
        db.update("INSERT INTO dbo.payroll_batches VALUES(10,1,'DA_CHOT',1,CURRENT_TIMESTAMP),(11,1,'DA_CHOT',1,CURRENT_TIMESTAMP),(12,1,'NHAP',NULL,NULL)");
        db.update("INSERT INTO dbo.payroll_details VALUES(101,10,1),(111,11,1),(112,11,2),(113,11,3),(121,12,1)");
        db.update("INSERT INTO dbo.employee_assignments VALUES(1,1,'2020-01-01',NULL),(2,1,'2020-01-01','2026-11-15'),(2,2,'2026-11-15',NULL),(3,1,'2020-01-01','2026-11-30'),(3,2,'2026-11-30',NULL)");
        db.update("INSERT INTO dbo.payroll_lines VALUES(1,101,1,'OLD','Old amount','ADD',500,NULL),(2,111,1,'LUONG','Saved salary','ADD',9007199254740993.1234,'Stored'),(3,111,2,'PHU_CAP','Saved allowance','ADD',100,NULL),(4,111,3,'TRU','Saved deduction','DEDUCT',250,NULL),(5,112,1,'LUONG','Other employee','ADD',2000,NULL),(6,121,1,'LUONG','Draft','ADD',999,NULL),(7,113,1,'LUONG','Last day transfer','ADD',300,NULL)");
        var service = new ReportDepartmentService(new NamedParameterJdbcTemplate(db));
        var from = LocalDate.of(2026,11,1); var to = LocalDate.of(2026,11,30);
        var result = service.payrollEmployees(from, to, 11, 1L, 0, 10);
        assertEquals(1, result.totalElements());
        assertEquals(111, result.items().get(0).payrollDetailId());
        assertEquals(0, result.items().get(0).totals().netPayroll().compareTo(new BigDecimal("9007199254740843.1234")));
        assertEquals(3, service.payrollEmployees(from, to, 11, null, 0, 10).totalElements());
        assertEquals(0, service.payrollEmployees(from, to, 11, 2L, 0, 10).totalElements());
        assertEquals(0, service.payrollEmployees(from, to, 12, null, 0, 10).totalElements());
        assertEquals(0, service.payrollEmployees(from.plusDays(1), to, 11, null, 0, 10).totalElements());
        assertEquals(0, service.payrollEmployees(from, to.minusDays(1), 11, null, 0, 10).totalElements());
        assertEquals(0, service.payrollEmployees(from, to, 10, null, 0, 10).items().get(0).totals().grossPayroll().compareTo(new BigDecimal("500")));
        var lines = service.payrollLines(from, to, 11, 111, 1L);
        assertEquals(3, lines.size());
        assertEquals("Saved salary", lines.get(0).componentName());
        assertEquals(new BigDecimal("9007199254740993.1234"), lines.get(0).amount());
        assertThrows(org.springframework.web.server.ResponseStatusException.class, () -> service.payrollLines(from, to, 10, 111, null));
        assertThrows(org.springframework.web.server.ResponseStatusException.class, () -> service.payrollLines(from, to, 11, 112, 1L));
        assertThrows(org.springframework.web.server.ResponseStatusException.class, () -> service.payrollLines(from, to, 11, 113, 1L));
        assertThrows(org.springframework.web.server.ResponseStatusException.class, () -> service.payrollLines(from, to.minusDays(1), 11, 111, null));
        assertThrows(org.springframework.web.server.ResponseStatusException.class, () -> service.payrollLines(from, to, 12, 121, null));
        assertThrows(IllegalArgumentException.class, () -> service.payrollEmployees(from, to, 11, null, 0, 101));
        // A period ending the next day must not be admitted by the report date filter.
        db.update("UPDATE dbo.payroll_periods SET period_end='2026-12-01' WHERE period_id=1");
        assertEquals(0, service.payrollEmployees(from, to, 11, null, 0, 10).totalElements());
        assertThrows(org.springframework.web.server.ResponseStatusException.class, () -> service.payrollLines(from, to, 11, 111, null));
        assertEquals(3, service.payrollEmployees(from, to.plusDays(1), 11, null, 0, 10).totalElements());
    }

    @Test void payrollUsesLatestFinalizedVersionAndEmployeesInDepartmentForWholePeriod() {
        var source=new DriverManagerDataSource("jdbc:h2:mem:report_department_payroll;MODE=MSSQLServer;DB_CLOSE_DELAY=-1","sa","");
        var db=new JdbcTemplate(source);
        db.execute("CREATE SCHEMA dbo");
        db.execute("CREATE TABLE dbo.payroll_periods(period_id bigint,period_code varchar(30),period_start date,period_end date)");
        db.execute("CREATE TABLE dbo.payroll_batches(batch_id bigint,period_id bigint,version_no int,status varchar(30),locked_by bigint,locked_at timestamp)");
        db.execute("CREATE TABLE dbo.payroll_details(payroll_detail_id bigint,batch_id bigint,employee_id bigint)");
        db.execute("CREATE TABLE dbo.payroll_lines(payroll_detail_id bigint,direction_snapshot varchar(10),component_code_snapshot varchar(30),amount decimal(20,4))");
        db.execute("CREATE TABLE dbo.employee_assignments(employee_id bigint,department_id bigint,effective_from date,effective_to date)");
        db.update("INSERT INTO dbo.payroll_periods VALUES(1,'P11','2026-11-01','2026-11-30')");
        db.update("INSERT INTO dbo.payroll_batches VALUES(10,1,1,'DA_CHOT',1,CURRENT_TIMESTAMP),(11,1,2,'DA_CHOT',1,CURRENT_TIMESTAMP),(12,1,3,'NHAP',NULL,NULL)");
        db.update("INSERT INTO dbo.employee_assignments VALUES(1,1,'2026-01-01',NULL),(2,1,'2026-01-01','2026-11-15'),(2,2,'2026-11-15',NULL),(3,2,'2026-01-01',NULL),(4,1,'2026-01-01','2026-11-30'),(4,2,'2026-11-30',NULL)");
        db.update("INSERT INTO dbo.payroll_details VALUES(101,10,1),(111,11,1),(112,11,2),(113,11,3),(114,11,4)");
        db.update("INSERT INTO dbo.payroll_lines VALUES(101,'ADD','LUONG',99999),(111,'ADD','LUONG',1000),(111,'DEDUCT','TRU',250),(112,'ADD','LUONG',2000),(113,'ADD','LUONG',700),(114,'ADD','LUONG',600)");
        var service=new ReportDepartmentService(new NamedParameterJdbcTemplate(db));
        var result=service.payroll(LocalDate.of(2026,11,1),LocalDate.of(2026,11,30),1);
        assertEquals(1,result.batchCount());
        assertEquals(1,result.payrollRecordCount());
        assertEquals(11L,result.batches().get(0).batchId());
        assertEquals(0,result.totals().grossPayroll().compareTo(new BigDecimal("1000")));
        assertEquals(0,result.totals().netPayroll().compareTo(new BigDecimal("750")));
        assertEquals(LocalDate.of(2026,11,30),result.batches().get(0).periodEnd());
        assertEquals(0,service.payroll(LocalDate.of(2026,11,1),LocalDate.of(2026,11,29),1).batchCount());
        assertEquals(0,service.payroll(LocalDate.of(2026,11,2),LocalDate.of(2026,11,30),1).batchCount());
        assertEquals(0,service.payroll(LocalDate.of(2026,11,1),LocalDate.of(2026,11,30),9).batchCount());
        db.update("UPDATE dbo.payroll_periods SET period_end='2026-12-01' WHERE period_id=1");
        assertEquals(0,service.payroll(LocalDate.of(2026,11,1),LocalDate.of(2026,11,30),1).batchCount());
        assertEquals(1,service.payroll(LocalDate.of(2026,11,1),LocalDate.of(2026,12,1),1).batchCount());
    }
}
