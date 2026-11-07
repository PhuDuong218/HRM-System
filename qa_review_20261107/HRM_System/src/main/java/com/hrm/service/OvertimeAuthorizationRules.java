package com.hrm.service;

/**
 * Pure, independently testable authorization decisions.
 * Matrix (confirmed with the business owner):
 * - EMPLOYEE: may only submit own OT and view own OT; never
 * approves/records/confirms.
 * - HR: may view OT in its department scope, including other HR; handles only
 * EMPLOYEE OT and never handles its own OT.
 * - ADMIN: administrator only, does not clock in or register OT.
 * - ADMIN: may manage EMPLOYEE/HR requests globally, including both approval
 * and confirmation.
 * - All state transitions must be audited.
 */
public final class OvertimeAuthorizationRules {
    private OvertimeAuthorizationRules() {
    }

    public static boolean canRegister(String actorRole) {
        return "EMPLOYEE".equals(actorRole) || "HR".equals(actorRole);
    }

    public static boolean isEligibleApplicant(String applicantRole) {
        return canRegister(applicantRole);
    }

    private static boolean isSelf(Long actorEmployeeId, long applicantEmployeeId) {
        return actorEmployeeId != null && actorEmployeeId == applicantEmployeeId;
    }

    public static boolean canApprove(String actorRole, String applicantRole,
            Long actorEmployeeId, long applicantEmployeeId, boolean withinHrScope) {
        if (!isEligibleApplicant(applicantRole))
            return false;
        if ("ADMIN".equals(actorRole))
            return true;
        if (isSelf(actorEmployeeId, applicantEmployeeId))
            return false;
        return "HR".equals(actorRole) && "EMPLOYEE".equals(applicantRole)
                && actorEmployeeId != null && withinHrScope;
    }

    /**
     * Recording actual time and resolving overflow follow the same rule as
     * approval.
     */
    public static boolean canRecordActual(String actorRole, String applicantRole,
            Long actorEmployeeId, long applicantEmployeeId, boolean withinHrScope) {
        return canApprove(actorRole, applicantRole, actorEmployeeId, applicantEmployeeId, withinHrScope);
    }

    public static boolean canConfirm(String actorRole, String applicantRole,
            Long actorEmployeeId, long applicantEmployeeId,
            long approvedByAccountId, long actorAccountId, boolean withinHrScope) {
        if (!isEligibleApplicant(applicantRole))
            return false;
        if ("ADMIN".equals(actorRole))
            return actorAccountId != approvedByAccountId;
        if (isSelf(actorEmployeeId, applicantEmployeeId))
            return false;
        return "HR".equals(actorRole) && "EMPLOYEE".equals(applicantRole)
                && actorEmployeeId != null && actorAccountId != approvedByAccountId && withinHrScope;
    }

    public static boolean canView(String actorRole, String applicantRole,
            Long actorEmployeeId, long applicantEmployeeId, boolean withinHrScope) {
        if (isSelf(actorEmployeeId, applicantEmployeeId) && canRegister(actorRole))
            return true;
        if ("ADMIN".equals(actorRole))
            return true;
        return "HR".equals(actorRole) && withinHrScope;
    }
}
