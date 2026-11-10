package com.hrm.service;

import java.io.FileInputStream;
import java.sql.DriverManager;
import java.time.LocalDate;
import java.util.Properties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import com.hrm.export.ReportExcelWriter;
import java.io.ByteArrayInputStream;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import static org.junit.jupiter.api.Assertions.*;

/** Explicit opt-in: exercises report SQL against the configured database without writing. */
@EnabledIfSystemProperty(named="hrm.report-monitor.readonly",matches="true")
class ReportDepartmentSqlServerReadOnlyTest {
    @Test void readsAllFilteredReportsWithoutMutatingData() throws Exception {
        var properties=new Properties();
        try(var input=new FileInputStream("src/main/resources/application-local.properties")){properties.load(input);}
        try(var connection=DriverManager.getConnection(properties.getProperty("spring.datasource.url"),properties.getProperty("spring.datasource.username"),properties.getProperty("spring.datasource.password"))){
            connection.setReadOnly(true);
            var db=new JdbcTemplate(new SingleConnectionDataSource(connection,true));
            var reports=new ReportDepartmentService(new NamedParameterJdbcTemplate(db));
            var departments=reports.departments();
            assertFalse(departments.isEmpty());
            long department=departments.get(0).id();
            var today=LocalDate.now(); var from=today.minusMonths(3).withDayOfMonth(1);
            var employees=reports.employees(department);
            assertTrue(employees.byDepartment().stream().allMatch(row->row.getGroupId()==department));
            assertEquals(employees.byDepartment().stream().mapToLong(row->row.getEmployeeCount()).sum(),
                employees.byStatus().stream().mapToLong(row->row.getEmployeeCount()).sum());
            for (var row : employees.byDepartment())
                assertEquals(row.getEmployeeCount().longValue(), reports.employeeDetails("department",row.getGroupId(),row.getGroupCode(),department,0,10).totalElements());
            for (var row : employees.byPosition())
                assertEquals(row.getEmployeeCount().longValue(), reports.employeeDetails("position",row.getGroupId(),row.getGroupCode(),department,0,10).totalElements());
            for (var row : employees.byStatus())
                assertEquals(row.getEmployeeCount().longValue(), reports.employeeDetails("status",row.getGroupId(),row.getGroupCode(),department,0,10).totalElements());
            for (var row : employees.byQualification())
                assertEquals(row.getEmployeeCount().longValue(), reports.employeeDetails("qualification",row.getGroupId(),row.getGroupCode(),department,0,10).totalElements());
            assertNotNull(reports.movements(from,today,null));
            var movements=reports.movements(from,today,department);
            assertNotNull(movements);
            try(var book=new XSSFWorkbook(new ByteArrayInputStream(new ReportExcelWriter().employees(employees,false,department,from,today,movements)))) {
                assertNotNull(book.getSheet("Bien dong"));
                assertNotNull(book.getSheet("Ky bien dong"));
                assertEquals(from.toString(),book.getSheet("Ky bien dong").getRow(1).getCell(1).getStringCellValue());
            }
            for(String kind:new String[]{"expiring","expired"}) {
                var result=reports.contracts(from,today,today,30,department,kind,0,10);
                assertTrue(result.items().size()<=10);
                assertTrue(result.totalElements()>=result.items().size());
                for(var item:result.items()) {
                    if(kind.equals("expiring")) assertFalse(item.endDate().isBefore(today));
                    else assertTrue(item.endDate().isBefore(today));
                }
            }
            var work=reports.work(from,today,department);
            assertTrue(work.confirmedWorkDays()>=0);
            assertTrue(work.approvedOvertimeHours().signum()>=0);
            var attendance=db.queryForObject("""
                SELECT COUNT(*) FROM dbo.attendance_records ar WHERE ar.work_date BETWEEN ? AND ?
                  AND ar.verification_status='DA_XAC_NHAN' AND ar.confirmed_by IS NOT NULL AND ar.confirmed_at IS NOT NULL
                  AND ar.invalidated_at IS NULL AND ar.attendance_result='DI_LAM'
                  AND EXISTS(SELECT 1 FROM dbo.employee_assignments ea WHERE ea.employee_id=ar.employee_id AND ea.department_id=? AND ea.effective_from<=ar.work_date AND (ea.effective_to IS NULL OR ea.effective_to>ar.work_date))
                """,Long.class,from,today,department);
            assertEquals(attendance.longValue(),work.confirmedWorkDays());
            var payroll=reports.payroll(from,today,department);
            assertEquals(payroll.totals().grossPayroll().subtract(payroll.totals().deductions()),payroll.totals().netPayroll());
            String globalSql=com.hrm.repository.PayrollStatisticsRepository.class
                .getMethod("summarizeFinalizedBatches",LocalDate.class,LocalDate.class)
                .getAnnotation(org.springframework.data.jpa.repository.Query.class).value();
            var globalParams=new org.springframework.jdbc.core.namedparam.MapSqlParameterSource("fromDate",from).addValue("toDate",today);
            var globalEnds=new NamedParameterJdbcTemplate(db).query(globalSql,globalParams,(r,n)->r.getDate("periodEnd").toLocalDate());
            assertTrue(globalEnds.stream().allMatch(end->!end.isAfter(today)));
            // Also exercise the SQL when the selected department has no finalized batch.
            assertEquals(0,reports.payrollEmployees(from,today,Long.MAX_VALUE,null,0,10).totalElements());
            assertThrows(org.springframework.web.server.ResponseStatusException.class,
                ()->reports.payrollLines(from,today,Long.MAX_VALUE,Long.MAX_VALUE,null));
            for (var batch : payroll.batches()) {
                assertFalse(batch.periodEnd().isAfter(today));
                assertEquals(db.queryForObject("SELECT period_end FROM dbo.payroll_periods WHERE period_id=?",java.sql.Date.class,batch.periodId()).toLocalDate(),batch.periodEnd());
                var detail=reports.payrollEmployees(from,today,batch.batchId(),department,0,10);
                assertEquals(batch.employeeCount(),detail.totalElements());
                for (var employee : detail.items()) {
                    var lines=reports.payrollLines(from,today,batch.batchId(),employee.payrollDetailId(),department);
                    var gross=lines.stream().filter(line->line.direction().equals("ADD")).map(ReportDepartmentService.PayrollLine::amount).reduce(java.math.BigDecimal.ZERO,java.math.BigDecimal::add);
                    var deduct=lines.stream().filter(line->line.direction().equals("DEDUCT")).map(ReportDepartmentService.PayrollLine::amount).reduce(java.math.BigDecimal.ZERO,java.math.BigDecimal::add);
                    assertEquals(0,gross.subtract(deduct).compareTo(employee.totals().netPayroll()));
                }
            }
            assertThrows(IllegalArgumentException.class,()->reports.movements(today,from,department));
            assertThrows(IllegalArgumentException.class,()->reports.contracts(from,today,today,30,department,"other",0,10));
        }
    }
}
