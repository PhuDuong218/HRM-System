package com.hrm.service;

import com.hrm.dto.WorkTimeDTOs.ScheduleInput;
import com.hrm.dto.WorkTimeDTOs.ScheduleView;
import com.hrm.security.AuthenticatedAccount;
import java.sql.Timestamp;
import java.util.Objects;
import java.time.LocalDateTime;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

/**
 * RBAC for schedule writes. Called inside scheduleTransactionManager
 * transactions.
 */
@Service
public class ScheduleWritePermissionService {
    private final JdbcTemplate db;

    public ScheduleWritePermissionService(JdbcTemplate db) {
        this.db = db;
    }

    public void requireCreate(AuthenticatedAccount actor, long employeeId, LocalDateTime start) {
        requireAt(actor, employeeId, start);
    }

    public void requireUpdate(AuthenticatedAccount actor, ScheduleView previous, ScheduleInput next) {
        if (previous.employeeId() != next.employeeId()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Không được đổi chủ lịch");
        }
        requireAt(actor, previous.employeeId(), previous.plannedStart());
        if (!previous.plannedStart().equals(next.plannedStart())) {
            requireAt(actor, next.employeeId(), next.plannedStart());
        }
    }

    private void requireAt(AuthenticatedAccount actor, long employeeId, LocalDateTime start) {
        if (actor == null || actor.accountId() == null || actor.roleCode() == null || start == null) {
            deny();
        }
        // Read and retain shared locks until transaction commit. Do not trust a JWT
        // role alone.
        var actual = db.query("""
                SELECT r.role_code, a.employee_id FROM dbo.accounts a WITH (HOLDLOCK)
                JOIN dbo.roles r ON r.role_id=a.role_id
                WHERE a.account_id=? AND a.is_active=1
                """, (rs, row) -> new ActorRole(rs.getString(1), rs.getObject(2, Long.class)), actor.accountId());
        if (actual.size() != 1 || !actual.get(0).roleCode().equals(actor.roleCode())
                || !Objects.equals(actual.get(0).employeeId(), actor.employeeId())) {
            deny();
        }
        String role = actual.get(0).roleCode();
        if (!role.equals("ADMIN") && !role.equals("HR"))
            deny();

        // No new/ordinary edited schedules for resigned employees, or before hiring.
        Long eligible = db.queryForObject("""
                SELECT COUNT_BIG(*) FROM dbo.employees e WITH (HOLDLOCK)
                WHERE e.employee_id=? AND e.employment_status='DANG_LAM'
                  AND e.termination_date IS NULL AND e.hire_date <= CAST(? AS date)
                """, Long.class, employeeId, Timestamp.valueOf(start));
        if (eligible == null || eligible != 1L)
            deny();
        if (role.equals("ADMIN"))
            return;

        // HR may only manage employees with an active EMPLOYEE account.
        Long isEmployee = db.queryForObject("""
                SELECT COUNT_BIG(*) FROM dbo.accounts a WITH (HOLDLOCK)
                JOIN dbo.roles r ON r.role_id=a.role_id
                WHERE a.employee_id=? AND a.is_active=1 AND r.role_code='EMPLOYEE'
                """, Long.class, employeeId);
        if (isEmployee == null || isEmployee != 1L)
            deny();

        // effective_to is exclusive; UTC scope validity is evaluated on SQL Server.
        // HOLDLOCK holds read/range locks until the write transaction commits,
        // preventing concurrent scope revocation from invalidating the permission
        // mid-write.
        Long scoped = db.queryForObject("""
                SELECT COUNT_BIG(*)
                FROM dbo.employee_assignments ea WITH (HOLDLOCK)
                JOIN dbo.hr_department_scopes hs WITH (HOLDLOCK)
                  ON hs.department_id=ea.department_id
                WHERE ea.employee_id=?
                  AND ea.effective_from<=CAST(? AS date)
                  AND (ea.effective_to IS NULL OR ea.effective_to>CAST(? AS date))
                  AND hs.hr_account_id=? AND hs.revoked_at IS NULL
                  AND hs.effective_from<=SYSUTCDATETIME()
                  AND (hs.effective_to IS NULL OR hs.effective_to>SYSUTCDATETIME())
                """, Long.class, employeeId, Timestamp.valueOf(start), Timestamp.valueOf(start), actor.accountId());
        if (scoped == null || scoped < 1L)
            deny();
    }

    private record ActorRole(String roleCode, Long employeeId) {
    }

    private static void deny() {
        throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Không có quyền quản lý lịch");
    }
}
