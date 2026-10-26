package com.hrm.service;

import java.security.Principal;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/** Uses the server-authenticated principal, never an account ID supplied by a client. */
@Service
public class DepartmentHeadAccessService {
    private final JdbcTemplate jdbc;
    public DepartmentHeadAccessService(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public Long requireManager(Principal principal) {
        if (principal == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Cần đăng nhập để quản lý nhân sự");
        }
        var ids = jdbc.queryForList("""
            SELECT a.account_id FROM dbo.accounts a
            JOIN dbo.employees e ON e.employee_id=a.employee_id
            JOIN dbo.roles r ON r.role_id=a.role_id
            WHERE a.username=? AND a.is_active=1 AND e.employment_status='DANG_LAM'
              AND r.role_code IN ('ADMIN','HR')
              AND EXISTS (SELECT 1 FROM dbo.role_permissions rp
                JOIN dbo.permissions p ON p.permission_id=rp.permission_id
                WHERE rp.role_id=r.role_id AND p.permission_code='EMPLOYEE_MANAGE')
            """, Long.class, principal.getName());
        if (ids.size() != 1) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Không có quyền quản lý nhân sự");
        }
        return ids.get(0);
    }
}
