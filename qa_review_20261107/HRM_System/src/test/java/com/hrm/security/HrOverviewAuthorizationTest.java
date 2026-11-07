package com.hrm.security;

import com.hrm.controller.HrOverviewController;
import com.hrm.service.HrOverviewService;
import java.util.List;
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

class HrOverviewAuthorizationTest {
    @Configuration
    @EnableMethodSecurity
    static class MethodSecurityConfiguration {}

    @AfterEach
    void clearAuthentication() { SecurityContextHolder.clearContext(); }

    @Test
    void onlyHrCanRequestTheAggregateOverviewWithoutReportPermission() {
        try (var context = new AnnotationConfigApplicationContext()) {
            var service = mock(HrOverviewService.class);
            context.register(MethodSecurityConfiguration.class);
            context.registerBean(HrOverviewService.class, () -> service);
            context.register(HrOverviewController.class);
            context.refresh();
            var controller = context.getBean(HrOverviewController.class);
            var actor = new AuthenticatedAccount(7L, 9L, "hr", "HR");
            var result = new HrOverviewService.Overview(null, null, null);
            when(service.get(actor)).thenReturn(result);

            authenticate("ROLE_HR");
            assertSame(result, controller.overview(actor));
            verify(service).get(actor);
            clearInvocations(service);

            for (var authorities : List.of(
                    List.of("ROLE_EMPLOYEE", "REPORT_VIEW"), List.of("ROLE_ADMIN", "REPORT_VIEW"))) {
                authenticate(authorities.toArray(String[]::new));
                assertThrows(AccessDeniedException.class, () -> controller.overview(actor));
                verifyNoInteractions(service);
            }
        }
    }

    private void authenticate(String... authorities) {
        var grants = java.util.Arrays.stream(authorities).map(SimpleGrantedAuthority::new).toList();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("test", "", grants));
    }
}
