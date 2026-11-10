package com.hrm.security;

import com.hrm.controller.PayrollStatisticsController;
import com.hrm.controller.ReportExportController;
import com.hrm.controller.EmployeeStatisticsController;
import com.hrm.controller.ContractStatisticsController;
import com.hrm.controller.WorkStatisticsController;
import com.hrm.controller.ReportMonitoringController;
import com.hrm.service.PayrollStatisticsService;
import com.hrm.service.ReportExportService;
import com.hrm.service.HrScopedReportService;
import com.hrm.service.EmployeeStatisticsService;
import com.hrm.service.ContractStatisticsService;
import com.hrm.service.WorkStatisticsService;
import com.hrm.service.ReportDepartmentService;
import com.hrm.dto.ReportExportFile;
import java.time.LocalDate;
import java.util.Arrays;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ReportAccessAuthorizationTest {
    @Configuration @EnableMethodSecurity static class SecurityConfiguration {}
    @AfterEach void clear() { SecurityContextHolder.clearContext(); }

    @Test
    void payrollViewingAndExportRequireTheSeparatePayrollPermission() {
        try (var context = new AnnotationConfigApplicationContext()) {
            var payroll = mock(PayrollStatisticsService.class);
            var exports = mock(ReportExportService.class);
            context.register(SecurityConfiguration.class);
            context.registerBean(ReportDepartmentService.class, () -> mock(ReportDepartmentService.class));
            context.registerBean(HrScopedReportService.class, () -> mock(HrScopedReportService.class));
            context.registerBean(PayrollStatisticsService.class, () -> payroll);
            context.registerBean(ReportExportService.class, () -> exports);
            context.register(PayrollStatisticsController.class, ReportExportController.class);
            context.refresh();
            var controller = context.getBean(PayrollStatisticsController.class);
            var exportController = context.getBean(ReportExportController.class);
            LocalDate date = LocalDate.of(2026, 11, 2);
            authenticate("ROLE_ADMIN", "REPORT_VIEW");
            assertThrows(AccessDeniedException.class, () -> controller.getPayrollStatistics(date, date));
            assertThrows(AccessDeniedException.class, () -> exportController.exportPayroll(date, date));
            authenticate("ROLE_ADMIN", "REPORT_VIEW", "PAYROLL_VIEW");
            assertDoesNotThrow(() -> controller.getPayrollStatistics(date, date));
            authenticate("ROLE_HR", "REPORT_VIEW", "PAYROLL_VIEW");
            assertThrows(AccessDeniedException.class, () -> controller.getPayrollStatistics(date, date));
            assertThrows(AccessDeniedException.class, () -> exportController.exportPayroll(date, date));
            authenticate("ROLE_EMPLOYEE", "REPORT_VIEW", "PAYROLL_VIEW");
            assertThrows(AccessDeniedException.class, () -> controller.getPayrollStatistics(date, date));
            assertThrows(AccessDeniedException.class, () -> exportController.exportPayroll(date, date));
        }
    }

    @Test
    void reportsAndExportsAreAdminOnlyEvenWhenHrHasReportPermission() {
        try (var context = new AnnotationConfigApplicationContext()) {
            var employee = mock(EmployeeStatisticsService.class);
            var contracts = mock(ContractStatisticsService.class);
            var work = mock(WorkStatisticsService.class);
            var exports = mock(ReportExportService.class);
            context.register(SecurityConfiguration.class);
            context.registerBean(ReportDepartmentService.class, () -> mock(ReportDepartmentService.class));
            context.registerBean(HrScopedReportService.class, () -> mock(HrScopedReportService.class));
            context.registerBean(EmployeeStatisticsService.class, () -> employee);
            context.registerBean(ContractStatisticsService.class, () -> contracts);
            context.registerBean(WorkStatisticsService.class, () -> work);
            context.registerBean(ReportExportService.class, () -> exports);
            context.register(EmployeeStatisticsController.class, ContractStatisticsController.class,
                    WorkStatisticsController.class, ReportExportController.class);
            context.refresh();
            var e = context.getBean(EmployeeStatisticsController.class);
            var c = context.getBean(ContractStatisticsController.class);
            var w = context.getBean(WorkStatisticsController.class);
            var x = context.getBean(ReportExportController.class);
            var date = LocalDate.of(2026, 11, 2);
            authenticate("ROLE_HR", "REPORT_VIEW", "PAYROLL_VIEW");
            assertThrows(AccessDeniedException.class, e::getEmployeeStatistics);
            assertThrows(AccessDeniedException.class, () -> c.getContractStatistics(date,date,date,30,null));
            assertThrows(AccessDeniedException.class, () -> w.getWorkStatistics(date,date));
            assertThrows(AccessDeniedException.class, x::exportEmployees);
            assertThrows(AccessDeniedException.class, () -> x.exportContracts(date,date,date,30,null));
            assertThrows(AccessDeniedException.class, () -> x.exportWork(date,date));
            authenticate("ROLE_ADMIN", "REPORT_VIEW");
            assertDoesNotThrow(e::getEmployeeStatistics);
            assertDoesNotThrow(() -> c.getContractStatistics(date,date,date,30,null));
            assertDoesNotThrow(() -> w.getWorkStatistics(date,date));
            when(exports.exportEmployees()).thenReturn(new ReportExportFile("employees.xlsx", new byte[]{1}));
            assertEquals(200, x.exportEmployees().getStatusCode().value());
        }
    }

    private void authenticate(String... grants) {
        var authorities = Arrays.stream(grants).map(SimpleGrantedAuthority::new).toList();
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken("test", "", authorities));
    }

    @Test void monitoringKeepsReportAndPayrollPermissions() {
        try (var context=new AnnotationConfigApplicationContext()) {
            context.register(SecurityConfiguration.class);
            context.registerBean(ReportDepartmentService.class,()->mock(ReportDepartmentService.class));
            context.register(ReportMonitoringController.class);
            context.refresh();
            var monitor=context.getBean(ReportMonitoringController.class);
            var date=LocalDate.of(2026,11,2);
            authenticate("ROLE_HR","REPORT_VIEW","PAYROLL_VIEW");
            assertThrows(AccessDeniedException.class,monitor::departments);
            assertThrows(AccessDeniedException.class,()->monitor.payroll(date,date,1L));
            assertThrows(AccessDeniedException.class,()->monitor.employeeDetails("status",null,"DANG_LAM",null,0,10));
            assertThrows(AccessDeniedException.class,()->monitor.payrollEmployees(11L,date,date,null,0,10));
            assertThrows(AccessDeniedException.class,()->monitor.payrollLines(11L,111L,date,date,null));
            authenticate("ROLE_ADMIN","REPORT_VIEW");
            assertDoesNotThrow(monitor::departments);
            assertDoesNotThrow(()->monitor.movements(date,date,null));
            assertThrows(AccessDeniedException.class,()->monitor.payroll(date,date,1L));
            assertDoesNotThrow(()->monitor.employeeDetails("status",null,"DANG_LAM",null,0,10));
            assertThrows(AccessDeniedException.class,()->monitor.payrollEmployees(11L,date,date,null,0,10));
            assertThrows(AccessDeniedException.class,()->monitor.payrollLines(11L,111L,date,date,null));
            authenticate("ROLE_ADMIN","PAYROLL_VIEW");
            assertThrows(AccessDeniedException.class,()->monitor.payrollEmployees(11L,date,date,null,0,10));
            assertThrows(AccessDeniedException.class,()->monitor.payrollLines(11L,111L,date,date,null));
            authenticate("ROLE_ADMIN","REPORT_VIEW","PAYROLL_VIEW");
            assertDoesNotThrow(()->monitor.payroll(date,date,1L));
            assertDoesNotThrow(()->monitor.payrollEmployees(11L,date,date,null,0,10));
            assertDoesNotThrow(()->monitor.payrollLines(11L,111L,date,date,null));
        }
    }
}
