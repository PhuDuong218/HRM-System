package com.hrm.security;

import com.hrm.model.Account;
import com.hrm.model.Role;
import com.hrm.enums.EmploymentStatus;
import com.hrm.repository.AccountRepository;
import com.hrm.repository.EmployeeRepository;
import com.hrm.repository.PermissionRepository;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

class JwtAuthenticationFilterTest {
    private final JwtService jwtService = mock(JwtService.class);
    private final AccountRepository accounts = mock(AccountRepository.class);
    private final EmployeeRepository employees = mock(EmployeeRepository.class);
    private final PermissionRepository permissions = mock(PermissionRepository.class);
    private final JwtAuthenticationFilter filter = new JwtAuthenticationFilter(jwtService, accounts, employees, permissions);

    @BeforeEach
    void clearBefore() {
        SecurityContextHolder.clearContext();
    }

    @AfterEach
    void clearAfter() {
        SecurityContextHolder.clearContext();
    }

    private Authentication send(String bearer) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/management/schedules/1");
        if (bearer != null)
            request.addHeader("Authorization", bearer);
        AtomicReference<Authentication> observed = new AtomicReference<>();
        FilterChain chain = (req, res) -> observed.set(SecurityContextHolder.getContext().getAuthentication());
        filter.doFilter(request, new MockHttpServletResponse(), chain);
        return observed.get();
    }

    private void mockClaims(long accountId, int version) {
        Claims claims = mock(Claims.class);
        when(claims.get("account_id", Long.class)).thenReturn(accountId);
        when(claims.get("token_version", Integer.class)).thenReturn(version);
        when(jwtService.parse("valid-token")).thenReturn(claims);
    }

    private Account account(boolean active, int version) {
        Account account = mock(Account.class);
        Role role = mock(Role.class);
        when(account.isActive()).thenReturn(active);
        when(account.getTokenVersion()).thenReturn(version);
        if (active) {
            when(account.getAccountId()).thenReturn(42L);
            when(account.getEmployeeId()).thenReturn(17L);
            when(account.getUsername()).thenReturn("hr.test");
            when(account.getRole()).thenReturn(role);
            when(role.getRoleCode()).thenReturn("HR");
            when(role.getRoleId()).thenReturn(2L);
        }
        return account;
    }

    @Test
    void missingAuthorizationHeaderDoesNotAuthenticate() throws Exception {
        assertNull(send(null));
        verifyNoInteractions(jwtService, accounts, permissions);
    }

    @Test
    void wrongAuthorizationSchemeDoesNotAuthenticate() throws Exception {
        assertNull(send("Basic x"));
        verifyNoInteractions(jwtService, accounts, permissions);
    }

    @Test
    void malformedTokenDoesNotAuthenticate() throws Exception {
        when(jwtService.parse("bad-token")).thenThrow(new JwtException("invalid"));
        assertNull(send("Bearer bad-token"));
        verifyNoInteractions(accounts, permissions);
    }

    @Test
    void unknownAccountDoesNotAuthenticate() throws Exception {
        mockClaims(42L, 3);
        when(accounts.findById(42L)).thenReturn(Optional.empty());
        assertNull(send("Bearer valid-token"));
        verifyNoInteractions(permissions);
    }

    @Test
    void inactiveAccountDoesNotAuthenticate() throws Exception {
        mockClaims(42L, 3);
        Account testAccount = account(false, 3);
        when(accounts.findById(42L)).thenReturn(Optional.of(testAccount));
        assertNull(send("Bearer valid-token"));
        verifyNoInteractions(permissions);
    }

    @Test
    void tokenVersionMismatchDoesNotAuthenticate() throws Exception {
        mockClaims(42L, 2);
        Account testAccount = account(true, 3);
        when(accounts.findById(42L)).thenReturn(Optional.of(testAccount));
        assertNull(send("Bearer valid-token"));
        verifyNoInteractions(permissions);
    }

    @Test
    void missingClaimsDoNotAuthenticate() throws Exception {
        Claims claims = mock(Claims.class);
        when(jwtService.parse("valid-token")).thenReturn(claims);
        assertNull(send("Bearer valid-token"));
        verify(accounts, never()).findById(anyLong());
    }

    @Test
    void activeAccountWithMatchingVersionAuthenticatesAndLoadsAuthorities() throws Exception {
        mockClaims(42L, 3);
        Account testAccount = account(true, 3);
        when(accounts.findById(42L)).thenReturn(Optional.of(testAccount));
        when(employees.existsByEmployeeIdAndEmploymentStatus(17L, EmploymentStatus.DANG_LAM)).thenReturn(true);
        when(permissions.findCodesByRoleId(2L)).thenReturn(Set.of("SELF_READ"));
        Authentication authentication = send("Bearer valid-token");
        assertNotNull(authentication);
        assertTrue(authentication.isAuthenticated());
        assertInstanceOf(AuthenticatedAccount.class, authentication.getPrincipal());
        AuthenticatedAccount principal = (AuthenticatedAccount) authentication.getPrincipal();
        assertEquals(42L, principal.accountId());
        assertEquals(17L, principal.employeeId());
        assertEquals("HR", principal.roleCode());
        assertTrue(authentication.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_HR")));
        assertTrue(authentication.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("SELF_READ")));
    }

    @Test
    void employeeNoLongerWorkingDoesNotAuthenticate() throws Exception {
        mockClaims(42L, 3);
        Account testAccount = account(true, 3);
        when(accounts.findById(42L)).thenReturn(Optional.of(testAccount));
        when(employees.existsByEmployeeIdAndEmploymentStatus(17L, EmploymentStatus.DANG_LAM)).thenReturn(false);
        assertNull(send("Bearer valid-token"));
        verifyNoInteractions(permissions);
    }
}
