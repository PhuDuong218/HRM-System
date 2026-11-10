package com.hrm.security;

import com.hrm.controller.HrDepartmentScopeController;
import com.hrm.dto.HrScopeDTOs.ReplaceDepartmentsRequest;
import java.util.Set;
import com.hrm.service.HrDepartmentScopeService;
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

class HrScopeGrantAllAuthorizationTest {
    @Configuration @EnableMethodSecurity
    static class SecurityConfiguration {}

    @AfterEach void clearAuthentication() { SecurityContextHolder.clearContext(); }

    @Test
    void onlyAdminWithAccountManageCanReplaceScope() {
        try (var context = new AnnotationConfigApplicationContext()) {
            var service = mock(HrDepartmentScopeService.class);
            context.register(SecurityConfiguration.class);
            context.registerBean(HrDepartmentScopeService.class, () -> service);
            context.register(HrDepartmentScopeController.class);
            context.refresh();
            var controller = context.getBean(HrDepartmentScopeController.class);
            var actor = new AuthenticatedAccount(1L, 1L, "admin", "ADMIN");
            var request = new ReplaceDepartmentsRequest(Set.of(), Set.of(3L), "Phân công phòng ban");

            authenticate("ROLE_ADMIN", "ACCOUNT_MANAGE");
            assertDoesNotThrow(() -> controller.replaceDepartments(actor, 2L, request));
            verify(service).replaceDepartments(actor, 2L, request);
            clearInvocations(service);

            for (var roles : List.of(List.of("ROLE_ADMIN"), List.of("ROLE_HR", "ACCOUNT_MANAGE"))) {
                authenticate(roles.toArray(String[]::new));
                assertThrows(AccessDeniedException.class, () -> controller.replaceDepartments(actor, 2L, request));
                verifyNoInteractions(service);
            }
        }
    }

    private void authenticate(String... authorities) {
        var grants = java.util.Arrays.stream(authorities).map(SimpleGrantedAuthority::new).toList();
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken("test", "", grants));
    }
}
