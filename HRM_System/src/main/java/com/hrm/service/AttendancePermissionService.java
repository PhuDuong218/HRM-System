package com.hrm.service;

import java.time.LocalDate;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

/**
 * Service-level authorization: caller must provide a trusted actor account id.
 */
@Service
public class AttendancePermissionService {
    public static final String MANAGE = "ATTENDANCE_MANAGE";
    public static final String CONFIRM = "ATTENDANCE_CONFIRM";
    public static final String UNCONFIRM = "ATTENDANCE_UNCONFIRM";

    public record Actor(long accountId, Long employeeId, String roleCode) {
    }

    private final JdbcTemplate db;
    private static final RowMapper<Actor> ACTOR_MAPPER = (rs, n) -> new Actor(
            rs.getLong("account_id"), rs.getObject("employee_id", Long.class), rs.getString("role_code"));

    public AttendancePermissionService(JdbcTemplate db) {
        this.db = db;
    }

    public Actor requirePermission(long accountId, String permissionCode) {
        if (accountId <= 0 || !isKnownPermission(permissionCode))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid actor or permission");
        var matches = db.query("""
                SELECT a.account_id, a.employee_id, r.role_code
                FROM dbo.accounts a WITH (HOLDLOCK)
                JOIN dbo.roles r ON r.role_id=a.role_id
                WHERE a.account_id=? AND a.is_active=1
                """, ACTOR_MAPPER, accountId);
        if (matches.size() != 1)
            deny();
        Actor actor = matches.get(0);
        if (!("ADMIN".equals(actor.roleCode()) || "HR".equals(actor.roleCode())))
            deny();
        if ("HR".equals(actor.roleCode()) && (actor.employeeId() == null || actor.employeeId() <= 0))
            deny();
        Long grants = db.queryForObject("""
                SELECT COUNT_BIG(*)
                FROM dbo.accounts a WITH (HOLDLOCK)
                JOIN dbo.role_permissions rp WITH (HOLDLOCK) ON rp.role_id=a.role_id
                JOIN dbo.permissions p WITH (HOLDLOCK) ON p.permission_id=rp.permission_id
                WHERE a.account_id=? AND a.is_active=1 AND p.permission_code=?
                """, Long.class, accountId, permissionCode);
        if (grants == null || grants != 1L)
            deny();
        return actor;
    }

    /** Checks actor at operation time; employee department at workDate. */
    public Actor requireAttendanceAccess(long actorId, long targetEmployeeId, LocalDate workDate,
            String permissionCode) {
        if (targetEmployeeId <= 0 || workDate == null)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid employee or work date");
        Actor actor = requirePermission(actorId, permissionCode);
        // Separation of duties applies to every management operation,
        // including manual creation and correction, not only confirmation.
        // Compare employee identities, never account_id to employee_id.
        if (actor.employeeId() != null && actor.employeeId() == targetEmployeeId)
            deny();
        Long eligible = db.queryForObject("""
                SELECT COUNT_BIG(*) FROM dbo.employees e WITH (HOLDLOCK)
                WHERE e.employee_id=? AND e.employment_status='DANG_LAM'
                  AND e.termination_date IS NULL AND e.hire_date<=?
                """, Long.class, targetEmployeeId, workDate);
        if (eligible == null || eligible != 1L)
            deny();
        if ("ADMIN".equals(actor.roleCode()))
            return actor;
        if (!"HR".equals(actor.roleCode()))
            deny();
        Long activeEmployee = db.queryForObject("""
                SELECT COUNT_BIG(*) FROM dbo.accounts a WITH (HOLDLOCK)
                JOIN dbo.roles r ON r.role_id=a.role_id
                WHERE a.employee_id=? AND a.is_active=1 AND r.role_code='EMPLOYEE'
                """, Long.class, targetEmployeeId);
        if (activeEmployee == null || activeEmployee != 1L)
            deny();
        Long scoped = db.queryForObject("""
                SELECT COUNT_BIG(*)
                FROM dbo.employee_assignments ea WITH (HOLDLOCK)
                JOIN dbo.hr_department_scopes hs WITH (HOLDLOCK) ON hs.department_id=ea.department_id
                WHERE ea.employee_id=? AND ea.effective_from<=?
                  AND (ea.effective_to IS NULL OR ea.effective_to>?)
                  AND hs.hr_account_id=? AND hs.revoked_at IS NULL
                  AND hs.effective_from<=SYSUTCDATETIME()
                  AND (hs.effective_to IS NULL OR hs.effective_to>SYSUTCDATETIME())
                """, Long.class, targetEmployeeId, workDate, workDate, actorId);
        if (scoped == null || scoped < 1L)
            deny();
        return actor;
    }

    private static boolean isKnownPermission(String p) {
        return MANAGE.equals(p) || CONFIRM.equals(p) || UNCONFIRM.equals(p);
    }

    private static void deny() {
        throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Attendance authorization denied");
    }
}
