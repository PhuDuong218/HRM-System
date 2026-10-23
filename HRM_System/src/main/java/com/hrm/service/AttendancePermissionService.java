package com.hrm.service;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

/**
 * Stage A: Service-level authorization only, NOT user authentication.
 * actorAccountId must come from trusted internal code, never an unauthenticated
 * HTTP request.
 * This component is NOT yet wired into AttendanceService.
 */
@Service
public class AttendancePermissionService {
    public static final String MANAGE = "ATTENDANCE_MANAGE";
    public static final String CONFIRM = "ATTENDANCE_CONFIRM";
    public static final String UNCONFIRM = "ATTENDANCE_UNCONFIRM";

    public record Actor(long accountId, long employeeId, String roleCode) {
    }

    private final JdbcTemplate db;
    private static final RowMapper<Actor> ACTOR_MAPPER = (rs, n) -> new Actor(rs.getLong("account_id"),
            rs.getLong("employee_id"), rs.getString("role_code"));

    public AttendancePermissionService(JdbcTemplate db) {
        this.db = db;
    }

    public Actor requirePermission(long accountId, String permissionCode) {
        if (accountId <= 0 || !isKnownPermission(permissionCode)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid actor or permission");
        }
        var matches = db.query("""
                SELECT a.account_id, a.employee_id, r.role_code
                FROM dbo.accounts a
                JOIN dbo.roles r ON r.role_id = a.role_id
                WHERE a.account_id = ? AND a.is_active = 1
                  AND a.employee_id IS NOT NULL
                """, ACTOR_MAPPER, accountId);
        if (matches.size() != 1) {
            deny();
        }
        Actor actor = matches.get(0);
        if (actor.employeeId() <= 0 || !("ADMIN".equals(actor.roleCode()) || "HR".equals(actor.roleCode()))) {
            deny();
        }
        Long grants = db.queryForObject("""
                SELECT COUNT_BIG(*)
                FROM dbo.accounts a
                JOIN dbo.role_permissions rp ON rp.role_id = a.role_id
                JOIN dbo.permissions p ON p.permission_id = rp.permission_id
                WHERE a.account_id = ? AND a.is_active = 1 AND p.permission_code = ?
                """, Long.class, accountId, permissionCode);
        if (grants == null || grants != 1) {
            deny();
        }
        return actor;
    }

    /**
     * Conservative object-level check. HR may confirm only a target with exactly
     * one account mapping to EMPLOYEE. Unknown/multiple/privileged mappings are
     * denied.
     * HR department scope and creator-vs-approver are NOT implemented yet and must
     * be validated before integrating this method into production write paths.
     */
    public void requireAttendanceAccess(long actorId, long targetEmployeeId, String permissionCode) {
        Actor actor = requirePermission(actorId, permissionCode);
        if (targetEmployeeId <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid employee");
        }
        boolean approval = CONFIRM.equals(permissionCode) || UNCONFIRM.equals(permissionCode);
        if (approval && actor.employeeId() == targetEmployeeId) {
            deny();
        }
        if ("ADMIN".equals(actor.roleCode())) {
            return;
        }
        if (!"HR".equals(actor.roleCode())) {
            deny();
        }
        // HR management scope is still undefined: no authorization to manage
        // other employees is implied merely by possession of a role grant.
        if (!approval) {
            deny();
        }
        var targetRoles = db.query("""
                SELECT r.role_code
                FROM dbo.accounts a
                JOIN dbo.roles r ON r.role_id = a.role_id
                WHERE a.employee_id = ?
                """, (rs, n) -> rs.getString("role_code"), targetEmployeeId);
        if (targetRoles.size() != 1 || !"EMPLOYEE".equals(targetRoles.get(0))) {
            deny();
        }
    }

    private static boolean isKnownPermission(String p) {
        return MANAGE.equals(p) || CONFIRM.equals(p) || UNCONFIRM.equals(p);
    }

    private static void deny() {
        throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Attendance authorization denied");
    }
}
