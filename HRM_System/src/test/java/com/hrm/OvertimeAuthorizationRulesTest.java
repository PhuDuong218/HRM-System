package com.hrm;

import com.hrm.service.OvertimeAuthorizationRules;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class OvertimeAuthorizationRulesTest {
    @Test

    void adminCannotApproveOrConfirmOwnOvertime() {
        assertFalse(OvertimeAuthorizationRules.canApprove(
                "ADMIN", "ADMIN", 3L, 3L, false));

        assertFalse(OvertimeAuthorizationRules.canConfirm(
                "ADMIN", "ADMIN", 3L, 3L, 1L, 1L, false));
    }

    @Test
    void hrOnlyForEmployeesInScope() {
        assertTrue(OvertimeAuthorizationRules.canApprove("HR", "EMPLOYEE", 2L, 3L, true));
        assertFalse(OvertimeAuthorizationRules.canApprove("HR", "HR", 2L, 3L, true));
        assertFalse(OvertimeAuthorizationRules.canApprove("HR", "EMPLOYEE", 2L, 3L, false));
        assertFalse(OvertimeAuthorizationRules.canApprove("HR", "EMPLOYEE", 3L, 3L, true));
    }

    @Test
    void confirmationRequiresDifferentApproverAndApplicantUnlessAdmin() {
        assertFalse(OvertimeAuthorizationRules.canConfirm("HR", "EMPLOYEE", 2L, 3L, 5L, 5L, true));
        assertFalse(OvertimeAuthorizationRules.canConfirm("HR", "EMPLOYEE", 3L, 3L, 5L, 6L, true));
        assertTrue(OvertimeAuthorizationRules.canConfirm("HR", "EMPLOYEE", 2L, 3L, 5L, 6L, true));
    }

    @Test
    void employeeCannotApprove() {
        assertFalse(OvertimeAuthorizationRules.canApprove("EMPLOYEE", "EMPLOYEE", 3L, 4L, true));
    }
}
