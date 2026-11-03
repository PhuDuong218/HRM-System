package com.hrm.service;

import com.hrm.security.AuthenticatedAccount;
import java.util.List;
import java.util.Objects;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

/**
 * Validate shift managers against the current account row, not solely JWT
 * claims.
 */
@Service
public class WorkShiftPermissionService {
    private final JdbcTemplate db;

    public WorkShiftPermissionService(JdbcTemplate db) {
        this.db = db;
    }

    public void requireView(AuthenticatedAccount actor) {
        verifyAccount(actor);
    }

    public void requireManage(AuthenticatedAccount actor) {
        verifyAccount(actor);
        if ("ADMIN".equals(actor.roleCode()))
            return;
        Long granted = db.queryForObject("""
                SELECT COUNT_BIG(*) FROM dbo.accounts a
                JOIN dbo.role_permissions rp ON rp.role_id=a.role_id
                JOIN dbo.permissions p ON p.permission_id=rp.permission_id
                WHERE a.account_id=? AND a.is_active=1 AND p.permission_code='WORK_SHIFT_MANAGE'
                """, Long.class, actor.accountId());
        if (granted == null || granted == 0)
            deny();
    }

    private void verifyAccount(AuthenticatedAccount actor) {
        if (actor == null || actor.accountId() == null || actor.roleCode() == null)
            deny();
        List<ActorRole> rows = db.query("""
                SELECT r.role_code, a.employee_id
                FROM dbo.accounts a WITH (HOLDLOCK)
                JOIN dbo.roles r ON r.role_id = a.role_id
                WHERE a.account_id = ? AND a.is_active = 1
                """, (rs, row) -> new ActorRole(rs.getString(1), rs.getObject(2, Long.class)), actor.accountId());
        if (rows.size() != 1 || !rows.get(0).role().equals(actor.roleCode())
                || !Objects.equals(rows.get(0).employeeId(), actor.employeeId()))
            deny();
        if (!rows.get(0).role().equals("ADMIN") && !rows.get(0).role().equals("HR"))
            deny();
    }

    private record ActorRole(String role, Long employeeId) {
    }

    private static void deny() {
        throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Không có quyền quản lý danh mục ca");
    }
}
