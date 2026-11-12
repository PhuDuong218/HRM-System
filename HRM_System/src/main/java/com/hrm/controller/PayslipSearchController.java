package com.hrm.controller;

import com.hrm.service.PersonnelScopeService;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class PayslipSearchController {
    public record Row(long payslipId, String payslipNumber, long employeeId, String employeeCode,
                      String employeeName, String periodCode, LocalDate periodStart, LocalDate periodEnd,
                      int versionNo) {}
    public record Directory(boolean canSelectEmployee, List<Row> rows) {}

    private final NamedParameterJdbcTemplate db;
    private final PersonnelScopeService scope;

    public PayslipSearchController(NamedParameterJdbcTemplate db, PersonnelScopeService scope) {
        this.db = db;
        this.scope = scope;
    }

    @GetMapping({"/api/payslips/directory", "/payslips/directory"})
    public ResponseEntity<Directory> directory() {
        var actor = PersonnelScopeService.current();
        if (actor == null) throw new AccessDeniedException("Yêu cầu đăng nhập");
        var auth = SecurityContextHolder.getContext().getAuthentication();
        boolean payrollAccess = auth.getAuthorities().stream().anyMatch(authority ->
            "PAYROLL_VIEW".equals(authority.getAuthority()) || "PAYROLL_MANAGE".equals(authority.getAuthority()));
        boolean admin = payrollAccess && "ADMIN".equals(actor.roleCode());
        boolean hr = payrollAccess && "HR".equals(actor.roleCode());

        var employeeIds = new ArrayList<Long>();
        if (actor.employeeId() != null) employeeIds.add(actor.employeeId());
        if (hr) employeeIds.addAll(scope.employees());
        if (!admin && employeeIds.isEmpty()) {
            return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(new Directory(false, List.of()));
        }

        List<Row> rows = db.query("""
            SELECT s.payslip_id, s.payslip_number, e.employee_id, e.employee_code, e.full_name,
                   p.period_code, p.period_start, p.period_end, b.version_no
            FROM dbo.payslips s
            JOIN dbo.payroll_details d ON d.payroll_detail_id=s.payroll_detail_id
            JOIN dbo.employees e ON e.employee_id=d.employee_id
            JOIN dbo.payroll_batches b ON b.batch_id=d.batch_id
            JOIN dbo.payroll_periods p ON p.period_id=b.period_id
            WHERE b.status='DA_CHOT' AND b.published_at IS NOT NULL
            """ + (admin ? "" : " AND e.employee_id IN (:employeeIds)") +
            " ORDER BY p.period_end DESC,b.version_no DESC,e.full_name,e.employee_id,s.payslip_id",
            admin ? Map.of() : Map.of("employeeIds", employeeIds),
            (rs, index) -> new Row(rs.getLong(1), rs.getString(2), rs.getLong(3),
                rs.getString(4), rs.getString(5), rs.getString(6),
                rs.getObject(7, LocalDate.class), rs.getObject(8, LocalDate.class), rs.getInt(9)));
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(new Directory(admin || hr, rows));
    }
}
