package com.hrm.service;

import com.hrm.dto.DecisionActor;
import com.hrm.exception.BusinessException;
import com.hrm.security.AuthenticatedAccount;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

@Component
public class SecurityDecisionActorProvider
        implements DecisionActorProvider {

    @Override
    public DecisionActor requireManager() {
        Authentication authentication =
                SecurityContextHolder.getContext().getAuthentication();

        if (authentication == null
                || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken) {
            throw new BusinessException(
                    HttpStatus.UNAUTHORIZED,
                    "Cần đăng nhập để thao tác quyết định");
        }

        if (!(authentication.getPrincipal()
                instanceof AuthenticatedAccount account)) {
            throw new BusinessException(
                    HttpStatus.UNAUTHORIZED,
                    "Danh tính đăng nhập không hợp lệ");
        }

        boolean manager = authentication.getAuthorities().stream()
                .anyMatch(authority ->
                        "ROLE_ADMIN".equals(authority.getAuthority())
                        || "ROLE_HR".equals(authority.getAuthority()));

        if (!manager) {
            throw new BusinessException(
                    HttpStatus.FORBIDDEN,
                    "Chỉ ADMIN hoặc HR được quản lý quyết định");
        }

        return new DecisionActor(
                account.accountId(),
                account.employeeId());
    }
}