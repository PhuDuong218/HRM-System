package com.hrm.security;

import com.hrm.controller.AuthController;
import com.hrm.controller.WorkScheduleController;
import com.hrm.dto.LoginResponse;
import com.hrm.model.Account;
import com.hrm.model.Role;
import com.hrm.repository.AccountRepository;
import com.hrm.repository.PermissionRepository;
import com.hrm.service.AuthService;
import com.hrm.service.ScheduleQueryService;
import com.hrm.service.WorkScheduleService;
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
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Security regression for public Auth/health routes and protected schedule
 * routes.
 * Uses the real SecurityFilterChain/JwtAuthenticationFilter/JwtService, mocked
 * repositories/services, and NO application DataSource or SQL connection.
 *
 * This isolated MVC context registers WorkScheduleController explicitly. It
 * does
 * NOT test its @ConditionalOnProperty activation in a full Boot application.
 */
@SpringJUnitWebConfig(classes = SharedApiSecurityRegressionTest.TestConfig.class)
class SharedApiSecurityRegressionTest {

    private static final String SECRET = "Shared-Api-Security-Regression-Test-HMAC-Secret-At-Least-32-Chars";

    @Configuration(proxyBeanMethods = false)
    @EnableWebMvc
    @Import(SecurityConfig.class)
    static class TestConfig {
        @Bean
        JwtService jwtService() {
            return new JwtService(SECRET, 3600L);
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
        JwtAuthenticationFilter jwtAuthenticationFilter(JwtService jwt,
                AccountRepository accounts, PermissionRepository permissions) {
            return new JwtAuthenticationFilter(jwt, accounts, permissions);
        }

        @Bean
        AuthService authService() {
            return mock(AuthService.class);
        }

        @Bean
        WorkScheduleService workScheduleService() {
            return mock(WorkScheduleService.class);
        }

        @Bean
        ScheduleQueryService scheduleQueryService() {
            return mock(ScheduleQueryService.class);
        }

        @Bean
        AuthController authController(AuthService service) {
            return new AuthController(service);
        }

        @Bean
        WorkScheduleController workScheduleController(
                WorkScheduleService service, ScheduleQueryService queries) {
            return new WorkScheduleController(service, queries);
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
    AuthService auth;
    @Autowired
    WorkScheduleService schedules;
    @Autowired
    ScheduleQueryService queries;
    @Autowired
    AccountRepository accounts;
    @Autowired
    PermissionRepository permissions;

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        reset(auth, schedules, queries, accounts, permissions);
        Filter securityChain = context.getBean("springSecurityFilterChain", Filter.class);
        assertNotNull(securityChain, "Real Spring Security filter chain must be installed");
        mvc = MockMvcBuilders.webAppContextSetup(context).addFilters(securityChain).build();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private String token(long id) {
        return jwt.issue(id, 12L, "it-shared-user", "HR", 1);
    }

    private void validAccount(long id) {
        Account account = mock(Account.class);
        Role role = mock(Role.class);
        when(account.getAccountId()).thenReturn(id);
        when(account.getEmployeeId()).thenReturn(12L);
        when(account.getUsername()).thenReturn("it-shared-user");
        when(account.getTokenVersion()).thenReturn(1);
        when(account.isActive()).thenReturn(true);
        when(account.getRole()).thenReturn(role);
        when(role.getRoleCode()).thenReturn("HR");
        when(role.getRoleId()).thenReturn(3L);
        when(accounts.findById(id)).thenReturn(Optional.of(account));
        when(permissions.findCodesByRoleId(3L)).thenReturn(Set.of());
    }

    @Test
    void loginWithoutJwtIsAllowedThroughSecurity() throws Exception {
        when(auth.login(any())).thenReturn(new LoginResponse("test-token", "Bearer", 3600L));
        mvc.perform(post("/api/auth/login")
                .contentType("application/json")
                .content("{\"username\":\"test-user\",\"password\":\"test-password\"}"))
                .andExpect(status().isOk());
        verify(auth).login(any());
    }

    @Test
    void invalidLoginBodyGets400WithoutJwt() throws Exception {
        mvc.perform(post("/api/auth/login")
                .contentType("application/json")
                .content("{\"username\":\"\",\"password\":\"\"}"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(auth);
    }

    @Test
    void logoutWithoutJwtGets401() throws Exception {
        mvc.perform(post("/api/auth/logout"))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(auth);
    }

    @Test
    void changePasswordWithoutJwtGets401() throws Exception {
        mvc.perform(post("/api/auth/change-password")
                .contentType("application/json")
                .content("{\"currentPassword\":\"old\",\"newPassword\":\"12345678\",\"confirmPassword\":\"12345678\"}"))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(auth);
    }

    @Test
    void protectedScheduleWithoutJwtGets401() throws Exception {
        mvc.perform(get("/api/management/schedules/44"))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(queries, schedules);
    }

    @Test
    void validJwtReachesScheduleController() throws Exception {
        validAccount(51L);
        // A deliberate service-level denial demonstrates controller was reached
        // and the authenticated actor was forwarded, without DB persistence.
        when(queries.getAuthorized(any(), eq(44L)))
                .thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "fixture not configured"));
        mvc.perform(get("/api/management/schedules/44")
                .header("Authorization", "Bearer " + token(51L)))
                .andExpect(status().isNotFound());
        verify(queries).getAuthorized(argThat(actor -> actor != null && Long.valueOf(51L).equals(actor.accountId())),
                eq(44L));
    }

    @Test
    void authenticatedButForbiddenScheduleGets403() throws Exception {
        validAccount(52L);
        when(queries.getAuthorized(any(), eq(45L)))
                .thenThrow(new ResponseStatusException(HttpStatus.FORBIDDEN, "out of scope"));
        mvc.perform(get("/api/management/schedules/45")
                .header("Authorization", "Bearer " + token(52L)))
                .andExpect(status().isForbidden());
        verify(queries).getAuthorized(any(), eq(45L));
    }

    @Test
    void healthPublicRouteIsNotBlockedBySecurity() throws Exception {
        // In isolated MVC there may be no /api/health handler: 404 is acceptable.
        // The invariant here is that Security does NOT return 401 or 403.
        mvc.perform(get("/api/health"))
                .andExpect(result -> {
                    int status = result.getResponse().getStatus();
                    assertNotEquals(401, status, "Public health route must not require JWT");
                    assertNotEquals(403, status, "Public health route must not be forbidden");
                });
    }
}
