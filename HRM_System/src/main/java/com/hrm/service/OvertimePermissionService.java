package com.hrm.service;

import com.hrm.security.AuthenticatedAccount;
import java.time.LocalDate;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

/**
 * Verify live DB account and HR's effective department scope; never trust
 * client supplied role/identity.
 */
@Service
public class OvertimePermissionService {
    private final JdbcTemplate db;

    public OvertimePermissionService(JdbcTemplate db) {
        this.db = db;
    }

    /**
     * SQL predicate: target employee is an EMPLOYEE-role account inside the
     * effective department scope of the HR account bound to the single '?'.
     */
    public static String hrScopeExistsSql(String employeeIdExpr, String dateExpr) {
        return "(EXISTS (SELECT 1 FROM dbo.employee_assignments ea "
                + "JOIN dbo.hr_department_scopes hs ON hs.department_id=ea.department_id "
                + "WHERE ea.employee_id=" + employeeIdExpr + " AND hs.hr_account_id=? "
                + "AND ea.effective_from<=" + dateExpr
                + " AND (ea.effective_to IS NULL OR ea.effective_to>" + dateExpr + ") "
                + "AND CONVERT(date,hs.effective_from)<=" + dateExpr
                + " AND (hs.effective_to IS NULL OR CONVERT(date,hs.effective_to)>" + dateExpr + ") "
                + "AND hs.revoked_at IS NULL) "
                + "AND EXISTS (SELECT 1 FROM dbo.accounts ta JOIN dbo.roles tr ON tr.role_id=ta.role_id "
                + "WHERE ta.employee_id=" + employeeIdExpr + " AND ta.is_active=1 AND tr.role_code='EMPLOYEE'))";
    }

    public record VerifiedActor(long accountId, Long employeeId, String roleCode) {
    }

    private static void deny() {
        throw new ResponseStatusException(HttpStatus.FORBIDDEN, "OT permission denied");
    }

    private static void invalid() {
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid OT actor or target");
    }

    public VerifiedActor active(AuthenticatedAccount principal) {
        if (principal == null || principal.accountId() == null || principal.accountId() <= 0) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required");
        }
        var list = db.query("""
                SELECT a.account_id,a.employee_id,r.role_code FROM dbo.accounts a
                JOIN dbo.roles r ON r.role_id=a.role_id
                WHERE a.account_id=? AND a.is_active=1
                """, (rs, i) -> new VerifiedActor(rs.getLong(1), rs.getObject(2, Long.class), rs.getString(3)),
                principal.accountId());
        if (list.size() != 1) {
            deny();
        }
        VerifiedActor a = list.get(0);
        if (!a.roleCode().equals(principal.roleCode()) ||
                (a.employeeId() == null ? principal.employeeId() != null
                        : !a.employeeId().equals(principal.employeeId())))
            deny();
        return a;
    }

    /**
     * Enforce live RBAC, not only JWT role claims. ADMIN's global business scope
     * does not eliminate the need for an explicitly granted RBAC permission.
     */
    public void requirePermission(VerifiedActor actor, String permissionCode) {
        Long matches = db.queryForObject("""
                SELECT COUNT_BIG(*) FROM dbo.accounts a
                JOIN dbo.role_permissions rp ON rp.role_id=a.role_id
                JOIN dbo.permissions p ON p.permission_id=rp.permission_id
                WHERE a.account_id=? AND a.is_active=1 AND p.permission_code=?
                """, Long.class, actor.accountId(), permissionCode);
        if (matches == null || matches == 0)
            deny();
    }

    public VerifiedActor requirePayrollRead(AuthenticatedAccount principal) {
        VerifiedActor actor = active(principal);
        // READ has no dedicated permission in supplied schema; role and scope are
        // enforced below.
        if (!("ADMIN".equals(actor.roleCode()) || "HR".equals(actor.roleCode())))
            deny();
        return actor;
    }

    public VerifiedActor requireSelfRegistration(AuthenticatedAccount principal) {
        var actor = active(principal);

        if (!("HR".equals(actor.roleCode())
                || "EMPLOYEE".equals(actor.roleCode()))) {
            deny();
        }

        if (actor.employeeId() == null || actor.employeeId() <= 0) {
            deny();
        }
        requirePermission(actor, "OVERTIME_REQUEST_CREATE");
        return actor;
    }

    public VerifiedActor requireApprove(AuthenticatedAccount principal, long targetEmployeeId, LocalDate otDate) {
        return requireAction(principal, targetEmployeeId, otDate, null, false, "OVERTIME_APPROVE");
    }

    /**
     * Record actual time, resolve overflow, cancel an approved request: same actors
     * as approval.
     */
    public VerifiedActor requireOperate(AuthenticatedAccount principal, long targetEmployeeId, LocalDate otDate) {
        return requireAction(principal, targetEmployeeId, otDate, null, false, "OVERTIME_APPROVE");
    }

    public VerifiedActor requireCancelApproved(AuthenticatedAccount principal, long targetEmployeeId,
            LocalDate otDate) {
        return requireAction(principal, targetEmployeeId, otDate, null, false, "OVERTIME_APPROVE");
    }

    public VerifiedActor requireOverflowReview(AuthenticatedAccount principal, long targetEmployeeId,
            LocalDate otDate) {
        return requireAction(principal, targetEmployeeId, otDate, null, false, "OVERTIME_APPROVE");
    }

    /** Owner-only actions (edit/cancel own pending request). */
    public VerifiedActor requireOwner(AuthenticatedAccount principal, long targetEmployeeId) {
        VerifiedActor actor = active(principal);
        if (!OvertimeAuthorizationRules.canRegister(actor.roleCode()) || actor.employeeId() == null
                || actor.employeeId() != targetEmployeeId)
            deny();
        requirePermission(actor, "OVERTIME_REQUEST_CREATE");
        return actor;
    }

    public VerifiedActor requireView(AuthenticatedAccount principal, long targetEmployeeId, LocalDate otDate) {
        VerifiedActor actor = active(principal);
        // READ has no dedicated permission in supplied schema; role and scope are
        // enforced below.
        if (actor.employeeId() != null && actor.employeeId() == targetEmployeeId
                && OvertimeAuthorizationRules.canRegister(actor.roleCode()))
            return actor;
        if ("ADMIN".equals(actor.roleCode()))
            return actor;
        String targetRole = roleOfEmployee(targetEmployeeId);
        boolean scoped = "HR".equals(actor.roleCode()) && inHrScope(actor.accountId(), targetEmployeeId, otDate);
        if (!OvertimeAuthorizationRules.canView(actor.roleCode(), targetRole, actor.employeeId(), targetEmployeeId,
                scoped))
            deny();
        return actor;
    }

    public VerifiedActor requireConfirm(AuthenticatedAccount principal, long targetEmployeeId, LocalDate otDate,
            long approverAccountId) {
        return requireAction(principal, targetEmployeeId, otDate, approverAccountId, true, "OVERTIME_APPROVE");
    }

    private VerifiedActor requireAction(AuthenticatedAccount principal, long target, LocalDate date, Long approver,
            boolean confirming, String permissionCode) {
        if (target <= 0 || date == null)
            invalid();
        VerifiedActor actor = active(principal);
        requirePermission(actor, permissionCode);
        String targetRole = roleOfEmployee(target);
        if (!OvertimeAuthorizationRules.isEligibleApplicant(targetRole))
            deny();
        if ("ADMIN".equals(actor.roleCode())) {
            // ADMIN manages other employees' OT; ADMIN never registers or accrues OT.
            boolean ok = confirming
                    ? OvertimeAuthorizationRules.canConfirm("ADMIN", null, actor.employeeId(), target, 0L,
                            actor.accountId(), false)
                    : OvertimeAuthorizationRules.canApprove("ADMIN", null, actor.employeeId(), target, false);
            if (!ok)
                deny();
            return actor;
        }
        boolean scoped = "HR".equals(actor.roleCode()) && inHrScope(actor.accountId(), target, date);
        boolean authorized = confirming
                ? OvertimeAuthorizationRules.canConfirm(actor.roleCode(), targetRole, actor.employeeId(), target,
                        approver, actor.accountId(), scoped)
                : OvertimeAuthorizationRules.canApprove(actor.roleCode(), targetRole, actor.employeeId(), target,
                        scoped);
        if (!authorized)
            deny();
        return actor;
    }

    public String roleOfEmployee(long employeeId) {
        return db.query("""
                SELECT r.role_code FROM dbo.accounts a JOIN dbo.roles r ON r.role_id=a.role_id
                WHERE a.employee_id=? AND a.is_active=1
                """, rs -> rs.next() ? rs.getString(1) : null, employeeId);
    }

    private boolean inHrScope(long accountId, long targetEmployeeId, LocalDate date) {
        Long count = db.queryForObject("""
                SELECT COUNT_BIG(*) FROM dbo.hr_department_scopes hs
                JOIN dbo.employee_assignments ta ON ta.department_id=hs.department_id
                WHERE hs.hr_account_id=? AND ta.employee_id=?
                  AND CONVERT(date,hs.effective_from)<=?
                  AND (hs.effective_to IS NULL OR CONVERT(date,hs.effective_to)>?)
                  AND hs.revoked_at IS NULL
                  AND ta.effective_from<=? AND (ta.effective_to IS NULL OR ta.effective_to>?)
                """, Long.class, accountId, targetEmployeeId, date, date, date, date);
        return count != null && count > 0;
    }
}
