package com.hrm.security;

/** Principal#getName must resolve to the login name across every module. */
public record AuthenticatedAccount(Long accountId, Long employeeId, String username, String roleCode)
        implements java.security.Principal {
    @Override public String getName() { return username; }
}
