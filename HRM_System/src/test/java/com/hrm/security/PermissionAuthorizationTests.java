package com.hrm.security;

import com.hrm.controller.AdminAccountController;
import com.hrm.controller.ContractStatisticsController;
import com.hrm.controller.DepartmentController;
import com.hrm.controller.EmployeeController;
import com.hrm.controller.EmployeeStatisticsController;
import com.hrm.controller.PayrollController;
import com.hrm.controller.PositionController;
import com.hrm.controller.WorkStatisticsController;
import com.hrm.dto.ResetPasswordRequest;
import com.hrm.service.AuthService;
import com.hrm.service.ContractStatisticsService;
import com.hrm.service.DepartmentService;
import com.hrm.service.EmployeeService;
import com.hrm.service.EmployeeStatisticsService;
import com.hrm.service.PayrollService;
import com.hrm.service.PositionService;
import com.hrm.service.WorkStatisticsService;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.function.Executable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class PermissionAuthorizationTests {

    private static final Long EMPLOYEE_ID = 9L;
    private static final Long OTHER_EMPLOYEE_ID = 10L;
    private static final LocalDate FROM = LocalDate.of(2026, 1, 1);
    private static final LocalDate TO = LocalDate.of(2026, 1, 31);

    private AnnotationConfigApplicationContext context;
    private Object[] services;

    @BeforeAll
    void startMethodSecurity() {
        context = new AnnotationConfigApplicationContext();
        context.register(MethodSecurityConfiguration.class);
        context.registerBean(EmployeeService.class, () -> mock(EmployeeService.class));
        context.registerBean(DepartmentService.class, () -> mock(DepartmentService.class));
        context.getBeanFactory().registerSingleton("positionService", mock(PositionService.class));
        context.registerBean(PayrollService.class, () -> mock(PayrollService.class));
        context.registerBean(EmployeeStatisticsService.class, () -> mock(EmployeeStatisticsService.class));
        context.registerBean(ContractStatisticsService.class, () -> mock(ContractStatisticsService.class));
        context.registerBean(WorkStatisticsService.class, () -> mock(WorkStatisticsService.class));
        context.registerBean(AuthService.class, () -> mock(AuthService.class));
        context.register(EmployeeController.class, DepartmentController.class, PositionController.class,
                PayrollController.class, EmployeeStatisticsController.class, ContractStatisticsController.class,
                WorkStatisticsController.class, AdminAccountController.class);
        context.refresh();
        services = new Object[] {
                context.getBean(EmployeeService.class), context.getBean(DepartmentService.class),
                context.getBean(PositionService.class), context.getBean(PayrollService.class),
                context.getBean(EmployeeStatisticsService.class), context.getBean(ContractStatisticsService.class),
                context.getBean(WorkStatisticsService.class), context.getBean(AuthService.class)
        };
    }

    @BeforeEach
    void clearState() {
        SecurityContextHolder.clearContext();
        clearInvocations(services);
    }

    @AfterEach
    void clearAuthentication() {
        SecurityContextHolder.clearContext();
    }

    @AfterAll
    void closeContext() {
        context.close();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("managementOperations")
    void managementRequiresTheSqlPermissionAndAManagementRole(ProtectedOperation operation) {
        for (String role : List.of("ADMIN", "HR")) {
            authenticate(role, operation.permission());
            assertDoesNotThrow(operation.action());

            clearInvocations(services);
            authenticate(role);
            assertThrows(AccessDeniedException.class, operation.action());
            verifyNoInteractions(services);

            // Old granular names must not silently grant access after the switch.
            authenticate(role, "employee.view", "employee.create", "employee.update", "employee.delete",
                    "department.view", "department.create", "department.update", "department.delete",
                    "position.view", "position.create", "position.update", "position.delete",
                    "employee.report.view", "contract.report.view", "work.report.view");
            assertThrows(AccessDeniedException.class, operation.action());
            verifyNoInteractions(services);
        }

        authenticate("EMPLOYEE", operation.permission());
        assertThrows(AccessDeniedException.class, operation.action());
        verifyNoInteractions(services);
    }

    @ParameterizedTest
    @ValueSource(strings = {"ADMIN", "HR", "EMPLOYEE"})
    void selfReadOnlyAllowsTheCurrentEmployeesProfileForEveryRole(String role) {
        Authentication authentication = authenticate(role, "SELF_READ");
        AuthenticatedAccount actor = (AuthenticatedAccount) authentication.getPrincipal();
        EmployeeController controller = context.getBean(EmployeeController.class);

        assertDoesNotThrow(() -> controller.getEmployee(EMPLOYEE_ID, authentication));
        verify(context.getBean(EmployeeService.class)).getEmployeeById(EMPLOYEE_ID, actor);

        clearInvocations(services);
        assertThrows(AccessDeniedException.class,
                () -> controller.getEmployee(OTHER_EMPLOYEE_ID, authentication));
        assertThrows(AccessDeniedException.class, () -> controller.getAllEmployees(authentication));
        verifyNoInteractions(services);
    }

    @ParameterizedTest
    @ValueSource(strings = {"ADMIN", "HR", "EMPLOYEE"})
    void publishedPersonalSalaryAndPayslipsRequirePayslipSelfRead(String role) {
        PayrollController controller = context.getBean(PayrollController.class);
        Authentication authentication = authenticate(role, "PAYSLIP_SELF_READ");
        AuthenticatedAccount actor = (AuthenticatedAccount) authentication.getPrincipal();

        assertDoesNotThrow(() -> controller.getMySalary(authentication));
        assertDoesNotThrow(() -> controller.getMyPayslips(authentication));
        verify(context.getBean(PayrollService.class)).getMySalary(actor);
        verify(context.getBean(PayrollService.class)).getMyPayslips(actor);

        clearInvocations(services);
        Authentication readOnlyProfile = authenticate(role, "SELF_READ");
        assertThrows(AccessDeniedException.class, () -> controller.getMySalary(readOnlyProfile));
        assertThrows(AccessDeniedException.class, () -> controller.getMyPayslips(readOnlyProfile));
        verifyNoInteractions(services);

        Authentication payrollManagement = authenticate(role, "PAYROLL_MANAGE", "PAYROLL_VIEW");
        assertThrows(AccessDeniedException.class, () -> controller.getMySalary(payrollManagement));
        assertThrows(AccessDeniedException.class, () -> controller.getMyPayslips(payrollManagement));
        verifyNoInteractions(services);
    }

    @Test
    void passwordResetRequiresAdminAndAccountManage() {
        AdminAccountController controller = context.getBean(AdminAccountController.class);
        ResetPasswordRequest request = new ResetPasswordRequest("NewPassword@123", "NewPassword@123");
        Authentication authentication = authenticate("ADMIN", "ACCOUNT_MANAGE");
        AuthenticatedAccount admin = (AuthenticatedAccount) authentication.getPrincipal();

        assertDoesNotThrow(() -> controller.resetPassword(admin, 2L, request));
        verify(context.getBean(AuthService.class)).resetPassword(admin.accountId(), 2L, request);

        clearInvocations(services);
        authenticate("ADMIN");
        assertThrows(AccessDeniedException.class, () -> controller.resetPassword(admin, 2L, request));

        for (String role : List.of("HR", "EMPLOYEE")) {
            Authentication denied = authenticate(role, "ACCOUNT_MANAGE");
            AuthenticatedAccount actor = (AuthenticatedAccount) denied.getPrincipal();
            assertThrows(AccessDeniedException.class, () -> controller.resetPassword(actor, 2L, request));
        }
        verifyNoInteractions(services);
    }

    @Test
    void managementCannotBeCalledWithoutAuthentication() {
        assertThrows(AuthenticationCredentialsNotFoundException.class,
                () -> context.getBean(DepartmentController.class).getAllDepartments());
        verifyNoInteractions(services);
    }

    private Authentication authenticate(String role, String... permissions) {
        AuthenticatedAccount principal = new AuthenticatedAccount(1L, EMPLOYEE_ID, "test-account", role);
        List<SimpleGrantedAuthority> authorities = new ArrayList<>();
        authorities.add(new SimpleGrantedAuthority("ROLE_" + role));
        for (String permission : permissions) {
            authorities.add(new SimpleGrantedAuthority(permission));
        }
        Authentication authentication =
                new UsernamePasswordAuthenticationToken(principal, null, authorities);
        SecurityContextHolder.getContext().setAuthentication(authentication);
        return authentication;
    }

    Stream<ProtectedOperation> managementOperations() {
        EmployeeController employee = context.getBean(EmployeeController.class);
        DepartmentController department = context.getBean(DepartmentController.class);
        PositionController position = context.getBean(PositionController.class);
        return Stream.of(
                new ProtectedOperation("GET /employees", "EMPLOYEE_MANAGE",
                        () -> employee.getAllEmployees(SecurityContextHolder.getContext().getAuthentication())),
                new ProtectedOperation("GET /employees/{id}", "EMPLOYEE_MANAGE",
                        () -> employee.getEmployee(OTHER_EMPLOYEE_ID,
                                SecurityContextHolder.getContext().getAuthentication())),
                new ProtectedOperation("POST /employees", "EMPLOYEE_MANAGE", () -> employee.addEmployee(null)),
                new ProtectedOperation("PUT /employees/{id}", "EMPLOYEE_MANAGE",
                        () -> employee.updateEmployee(OTHER_EMPLOYEE_ID, null)),
                new ProtectedOperation("DELETE /employees/{id}", "EMPLOYEE_MANAGE",
                        () -> employee.deleteEmployee(OTHER_EMPLOYEE_ID)),
                new ProtectedOperation("GET /departments", "DEPARTMENT_MANAGE", department::getAllDepartments),
                new ProtectedOperation("GET /departments/{id}", "DEPARTMENT_MANAGE",
                        () -> department.getDepartment(1L)),
                new ProtectedOperation("POST /departments", "DEPARTMENT_MANAGE", () -> department.addDepartment(null)),
                new ProtectedOperation("PUT /departments/{id}", "DEPARTMENT_MANAGE",
                        () -> department.updateDepartment(1L, null)),
                new ProtectedOperation("DELETE /departments/{id}", "DEPARTMENT_MANAGE",
                        () -> department.deleteDepartment(1L)),
                new ProtectedOperation("GET /positions", "POSITION_MANAGE", position::getAllPositions),
                new ProtectedOperation("GET /positions/{id}", "POSITION_MANAGE", () -> position.getPositionById(1L)),
                new ProtectedOperation("POST /positions", "POSITION_MANAGE", () -> position.addPosition(null)),
                new ProtectedOperation("PUT /positions/{id}", "POSITION_MANAGE", () -> position.updatePosition(1L, null)),
                new ProtectedOperation("DELETE /positions/{id}", "POSITION_MANAGE", () -> position.deletePosition(1L)),
                new ProtectedOperation("GET /api/reports/employees", "REPORT_VIEW",
                        () -> context.getBean(EmployeeStatisticsController.class).getEmployeeStatistics()),
                new ProtectedOperation("GET /api/reports/contracts", "REPORT_VIEW",
                        () -> context.getBean(ContractStatisticsController.class)
                                .getContractStatistics(FROM, TO, TO, 30, null)),
                new ProtectedOperation("GET /api/reports/work", "REPORT_VIEW",
                        () -> context.getBean(WorkStatisticsController.class).getWorkStatistics(FROM, TO)));
    }

    private record ProtectedOperation(String endpoint, String permission, Executable action) {
        @Override
        public String toString() {
            return endpoint;
        }
    }

    @TestConfiguration(proxyBeanMethods = false)
    @EnableMethodSecurity
    static class MethodSecurityConfiguration {
    }
}
