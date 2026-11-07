
package com.hrm.security;

import com.hrm.enums.EmploymentStatus;
import com.hrm.model.Account;
import com.hrm.repository.AccountRepository;
import com.hrm.repository.EmployeeRepository;
import com.hrm.repository.PermissionRepository;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final AccountRepository accountRepository;
    private final EmployeeRepository employeeRepository;
    private final PermissionRepository permissionRepository;

    public JwtAuthenticationFilter(
            JwtService jwtService,
            AccountRepository accountRepository,
            EmployeeRepository employeeRepository,
            PermissionRepository permissionRepository) {

        this.jwtService = jwtService;
        this.accountRepository = accountRepository;
        this.employeeRepository = employeeRepository;
        this.permissionRepository = permissionRepository;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain)
            throws ServletException, IOException {

        String header = request.getHeader("Authorization");

        if (header != null && header.startsWith("Bearer ")) {
            authenticate(header.substring(7));
        }

        filterChain.doFilter(request, response);
    }

    private void authenticate(String token) {
        SecurityContextHolder.clearContext();

        try {
            Claims claims = jwtService.parse(token);

            Long accountId = claims.get("account_id", Long.class);
            Integer tokenVersion = claims.get("token_version", Integer.class);

            if (accountId == null || tokenVersion == null) {
                return;
            }

            Account account = accountRepository.findById(accountId).orElse(null);

            if (account == null
                    || !account.isActive()
                    || account.getTokenVersion() != tokenVersion
                    || !employeeRepository.existsByEmployeeIdAndEmploymentStatus(
                            account.getEmployeeId(),
                            EmploymentStatus.DANG_LAM)) {
                return;
            }

            AuthenticatedAccount principal = new AuthenticatedAccount(
                    account.getAccountId(),
                    account.getEmployeeId(),
                    account.getUsername(),
                    account.getRole().getRoleCode());

            UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                    principal,
                    null,
                    buildAuthorities(account));

            SecurityContextHolder.getContext().setAuthentication(authentication);

        } catch (JwtException | IllegalArgumentException ignored) {
            SecurityContextHolder.clearContext();
        }
    }

    private Set<SimpleGrantedAuthority> buildAuthorities(Account account) {
        Set<SimpleGrantedAuthority> authorities = new HashSet<>();

        authorities.add(new SimpleGrantedAuthority(
                "ROLE_" + account.getRole().getRoleCode()));

        permissionRepository.findCodesByRoleId(
                account.getRole().getRoleId()).forEach(code -> authorities.add(new SimpleGrantedAuthority(code)));

        return authorities;
    }
}
