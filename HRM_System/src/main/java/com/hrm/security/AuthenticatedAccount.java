package com.hrm.security;

public record AuthenticatedAccount(Long accountId, Long employeeId, String username, String roleCode) {
}
