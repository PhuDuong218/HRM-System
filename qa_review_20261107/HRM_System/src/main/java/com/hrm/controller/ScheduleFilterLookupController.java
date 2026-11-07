package com.hrm.controller;

import com.hrm.security.AuthenticatedAccount;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/** Read-only helpers for the schedules filter. No writes or schema changes. */
@RestController
public class ScheduleFilterLookupController {
    private final JdbcTemplate db;

    public ScheduleFilterLookupController(JdbcTemplate db) {
        this.db = db;
    }

    private void requireManagement(AuthenticatedAccount actor) {
        if (actor == null || actor.accountId() == null || actor.roleCode() == null
                || !("ADMIN".equals(actor.roleCode()) || "HR".equals(actor.roleCode()))) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Không có quyền tra cứu lịch quản lý");
        }
    }

    public record DepartmentOption(long departmentId, String departmentName) {
    }

    public record EmployeeResolution(long employeeId) {
    }

    @GetMapping("/api/management/schedules/filter-departments")
    public List<DepartmentOption> departments(@AuthenticationPrincipal AuthenticatedAccount actor) {
        requireManagement(actor);
        if ("ADMIN".equals(actor.roleCode())) {
            return db.query("SELECT department_id,department_name FROM dbo.departments ORDER BY department_name",
                    (rs, i) -> new DepartmentOption(rs.getLong(1), rs.getString(2)));
        }
        return db.query("""
                SELECT DISTINCT d.department_id,d.department_name
                FROM dbo.departments d
                JOIN dbo.hr_department_scopes scope ON scope.department_id=d.department_id
                WHERE scope.hr_account_id=? AND scope.revoked_at IS NULL
                  AND scope.effective_from<=SYSUTCDATETIME()
                  AND (scope.effective_to IS NULL OR scope.effective_to>SYSUTCDATETIME())
                ORDER BY d.department_name
                """, (rs, i) -> new DepartmentOption(rs.getLong(1), rs.getString(2)), actor.accountId());
    }

    @GetMapping("/api/management/schedules/resolve-employee")
    public EmployeeResolution resolveEmployee(@AuthenticationPrincipal AuthenticatedAccount actor,
            @RequestParam String code) {
        requireManagement(actor);
        if (code == null || code.isBlank() || code.length() > 50)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Mã nhân viên không hợp lệ");
        String key = code.trim();
        // Numeric value can represent the internal employee_id, preserving old
        // workflows.
        boolean numeric = key.matches("[0-9]+");
        List<Long> ids;
        if ("ADMIN".equals(actor.roleCode())) {
            ids = db.query(numeric
                    ? "SELECT employee_id FROM dbo.employees WHERE employee_code=? OR employee_id=TRY_CONVERT(bigint,?)"
                    : "SELECT employee_id FROM dbo.employees WHERE employee_code=?",
                    (rs, i) -> rs.getLong(1), numeric ? new Object[] { key, key } : new Object[] { key });
        } else {
            String where = numeric ? "(e.employee_code=? OR e.employee_id=TRY_CONVERT(bigint,?))" : "e.employee_code=?";
            String sql = """
                    SELECT DISTINCT e.employee_id FROM dbo.employees e
                    JOIN dbo.employee_assignments a ON a.employee_id=e.employee_id
                    JOIN dbo.hr_department_scopes scope ON scope.department_id=a.department_id
                    WHERE %s AND scope.hr_account_id=? AND scope.revoked_at IS NULL
                      AND scope.effective_from<=SYSUTCDATETIME()
                      AND (scope.effective_to IS NULL OR scope.effective_to>SYSUTCDATETIME())
                      AND a.effective_from<=CAST(SYSUTCDATETIME() AS date)
                      AND (a.effective_to IS NULL OR a.effective_to>CAST(SYSUTCDATETIME() AS date))
                    """.formatted(where);
            ids = db.query(sql, (rs, i) -> rs.getLong(1),
                    numeric ? new Object[] { key, key, actor.accountId() } : new Object[] { key, actor.accountId() });
        }
        if (ids.size() != 1)
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy nhân viên trong phạm vi quản lý");
        return new EmployeeResolution(ids.get(0));
    }
}
