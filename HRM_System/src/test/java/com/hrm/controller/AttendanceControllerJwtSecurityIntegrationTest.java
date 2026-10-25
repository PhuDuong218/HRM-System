package com.hrm.controller;

import com.hrm.model.Account;
import com.hrm.model.Role;
import java.util.Set;
import com.hrm.repository.AccountRepository;
import com.hrm.repository.PermissionRepository;
import com.hrm.security.JwtAuthenticationFilter;
import com.hrm.security.JwtService;
import com.hrm.security.SecurityConfig;
import com.hrm.service.AttendanceService;
import jakarta.servlet.Filter;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.junit.jupiter.web.SpringJUnitWebConfig;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * IT-06B: genuine SecurityFilterChain + JwtAuthenticationFilter + JwtService,
 * with mocked persistence and mocked attendance service. NO SQL connection.
 *
 * IMPORTANT: missing/invalid-token 401 expectations are contract tests, not a
 * statement that the current SecurityConfig already guarantees HTTP 401.
 * If 403 is observed instead, stop and review configuration; do not modify it
 * without separate authorization.
 *
 * This is an isolated Spring MVC application, NOT a full Spring Boot
 * deployment.
 * It does not test API enablement conditional properties or real JDBC RBAC.
 */
@SpringJUnitWebConfig(classes = AttendanceControllerJwtSecurityIntegrationTest.TestConfig.class)
class AttendanceControllerJwtSecurityIntegrationTest {

    private static final String SECRET = "IT06B-Test-Only-Secret-Key-For-HMAC-Signing-At-Least-32-Characters";
    private static final String REASON = "Đối chiếu lại bảng công";

    @Configuration(proxyBeanMethods = false)
    @EnableWebMvc
    @Import(SecurityConfig.class)
    static class TestConfig {
        @Bean
        JwtService jwtService() {
            return new JwtService(SECRET, 3600L);
        }

        @Bean
        JwtAuthenticationFilter jwtAuthenticationFilter(JwtService jwt,
                AccountRepository accounts, PermissionRepository permissions) {
            return new JwtAuthenticationFilter(jwt, accounts, permissions);
        }

        @Bean
        AccountRepository accountRepository() {
            return mock(AccountRepository.class);
        }

        @Bean
        PermissionRepository permissionRepository() {
            return mock(PermissionRepository.class);
        }

        @Bean
        AttendanceService attendanceService() {
            return mock(AttendanceService.class);
        }

        @Bean
        AttendanceController attendanceController(AttendanceService attendance) {
            return new AttendanceController(attendance);
        }

        @Bean
        LocalValidatorFactoryBean validator() {
            return new LocalValidatorFactoryBean();
        }
    }

    @Autowired
    WebApplicationContext context;
    @Autowired
    JwtService jwt;
    @Autowired
    AccountRepository accounts;
    @Autowired
    PermissionRepository permissions;
    @Autowired
    AttendanceService attendance;

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        reset(accounts, permissions, attendance);
        Filter chain = context.getBean("springSecurityFilterChain", Filter.class);
        assertNotNull(chain, "Must use the real Spring Security filter chain");
        mvc = MockMvcBuilders.webAppContextSetup(context).addFilters(chain).build();
    }

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    private String token(long accountId, int version) {
        return jwt.issue(accountId, 10L, "it06b-user", "HR", version);
    }

    private void activeAccount(long id, int version, String roleCode, List<String> codes) {
        Account account = mock(Account.class);
        Role role = mock(Role.class);
        when(account.getAccountId()).thenReturn(id);
        when(account.getEmployeeId()).thenReturn(10L);
        when(account.getUsername()).thenReturn("it06b-user");
        when(account.isActive()).thenReturn(true);
        when(account.getTokenVersion()).thenReturn(version);
        when(account.getRole()).thenReturn(role);
        when(role.getRoleCode()).thenReturn(roleCode);
        when(role.getRoleId()).thenReturn(3L);
        when(accounts.findById(id)).thenReturn(Optional.of(account));
        when(permissions.findCodesByRoleId(3L)).thenReturn(Set.copyOf(codes));
    }

    @Test
    void noBearerTokenMustReturn401ByApiContract() throws Exception {
        mvc.perform(post("/api/attendance/51/confirm"))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(attendance);
    }

    @Test
    void wrongSignatureMustReturn401ByApiContract() throws Exception {
        JwtService otherKey = new JwtService(
                "Different-Test-Secret-Key-To-Prove-Invalid-Signature-123456789", 3600L);
        String invalid = otherKey.issue(51L, 10L, "it06b-user", "HR", 0);
        mvc.perform(post("/api/attendance/51/confirm")
                .header("Authorization", "Bearer " + invalid))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(attendance);
    }

    @Test
    void expiredJwtMustReturn401ByApiContract() throws Exception {
        JwtService expiredIssuer = new JwtService(SECRET, -120L);
        String expired = expiredIssuer.issue(51L, 10L, "it06b-user", "HR", 0);
        mvc.perform(post("/api/attendance/51/confirm")
                .header("Authorization", "Bearer " + expired))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(attendance);
    }

    @Test
    void inactiveAccountCannotReachController() throws Exception {
        Account inactive = mock(Account.class);
        when(inactive.isActive()).thenReturn(false);
        when(accounts.findById(51L)).thenReturn(Optional.of(inactive));
        mvc.perform(post("/api/attendance/51/confirm")
                .header("Authorization", "Bearer " + token(51L, 0)))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(attendance);
    }

    @Test
    void staleTokenVersionCannotReachController() throws Exception {
        activeAccount(51L, 2, "HR", List.of("ATTENDANCE_CONFIRM"));
        mvc.perform(post("/api/attendance/51/confirm")
                .header("Authorization", "Bearer " + token(51L, 1)))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(attendance);
    }

    @Test
    void validJwtPassesRealFilterAndUsesStoredActorId() throws Exception {
        activeAccount(51L, 1, "HR", List.of("ATTENDANCE_CONFIRM"));
        // Mocked service returning null: tests only authorization & actor identity.
        mvc.perform(post("/api/attendance/51/confirm")
                .header("Authorization", "Bearer " + token(51L, 1)))
                .andExpect(status().isOk());
        verify(attendance).confirm(51L, 51L);
    }

    @Test
    void missingPermissionDeniedByServiceAndPropagatedAs403() throws Exception {
        activeAccount(52L, 1, "EMPLOYEE", List.of());
        when(attendance.confirm(51L, 52L))
                .thenThrow(new ResponseStatusException(HttpStatus.FORBIDDEN, "Forbidden by attendance RBAC"));
        mvc.perform(post("/api/attendance/51/confirm")
                .header("Authorization", "Bearer " + token(52L, 1)))
                .andExpect(status().isForbidden());
        verify(attendance).confirm(51L, 52L);
    }

    @Test
    void unconfirmIgnoresSpoofedActorIdFromClient() throws Exception {
        activeAccount(53L, 1, "HR", List.of("ATTENDANCE_UNCONFIRM"));
        mvc.perform(post("/api/attendance/51/unconfirm")
                .header("Authorization", "Bearer " + token(53L, 1))
                .contentType("application/json")
                .content("{\"reason\":\"" + REASON + "\",\"actorAccountId\":999}"))
                .andExpect(status().isOk());
        verify(attendance).unconfirm(51L, REASON, 53L);
    }
}
