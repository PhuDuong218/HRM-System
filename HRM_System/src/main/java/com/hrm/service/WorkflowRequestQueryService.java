package com.hrm.service;

import com.hrm.dto.PageResponse;
import com.hrm.security.AuthenticatedAccount;
import java.time.LocalDate;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

/**
 * Read-only workflow tracking; never bypasses the underlying submit/decide
 * authorization.
 */
@Service
public class WorkflowRequestQueryService {
    public record RequestSummary(long requestId, long employeeId, String requestCode,
            String requestType, String status, String reason, java.time.LocalDateTime submittedAt,
            Long createdBy, Long processedBy) {
    }

    private final JdbcTemplate db;

    public WorkflowRequestQueryService(JdbcTemplate db) {
        this.db = db;
    }

    private record Actor(long accountId, Long employeeId, String role) {
    }

    private static ResponseStatusException denied() {
        return new ResponseStatusException(HttpStatus.FORBIDDEN, "Request access denied");
    }

    private Actor actor(AuthenticatedAccount principal) {
        if (principal == null || principal.accountId() == null)
            throw denied();
        var rows = db.query("""
                SELECT a.account_id,a.employee_id,r.role_code FROM dbo.accounts a
                JOIN dbo.roles r ON r.role_id=a.role_id WHERE a.account_id=? AND a.is_active=1
                """, (rs, i) -> new Actor(rs.getLong(1), rs.getObject(2, Long.class), rs.getString(3)),
                principal.accountId());
        if (rows.size() != 1)
            throw denied();
        Actor a = rows.get(0);
        if (!Objects.equals(a.employeeId(), principal.employeeId()) || !a.role().equals(principal.roleCode()))
            throw denied();
        if (!Set.of("ADMIN", "HR", "EMPLOYEE").contains(a.role()))
            throw denied();
        return a;
    }

    private static String type(String type) {
        if (!"DOI_LICH".equals(type) && !"SUA_CONG".equals(type))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported request type");
        return type;
    }

    private static final String FIELDS = "r.request_id,r.employee_id,r.request_code,r.request_type,r.status,r.reason,r.submitted_at,r.created_by,r.processed_by";
    private static final org.springframework.jdbc.core.RowMapper<RequestSummary> MAPPER = (rs, i) -> new RequestSummary(
            rs.getLong(1), rs.getLong(2), rs.getString(3), rs.getString(4), rs.getString(5),
            rs.getString(6), rs.getObject(7, java.time.LocalDateTime.class), rs.getObject(8, Long.class),
            rs.getObject(9, Long.class));

    private static String scopeClause(Actor a) {
        if ("ADMIN".equals(a.role()))
            return "";
        if ("EMPLOYEE".equals(a.role()))
            return " AND r.employee_id=?";
        // HR can see their own requests and employees belonging to an active HR scope.
        return """
                AND (r.employee_id=? OR (
                    EXISTS (SELECT 1 FROM dbo.accounts target JOIN dbo.roles tr ON tr.role_id=target.role_id
                            WHERE target.employee_id=r.employee_id AND target.is_active=1 AND tr.role_code='EMPLOYEE')
                    AND EXISTS (SELECT 1 FROM dbo.employee_assignments ea
                        JOIN dbo.hr_department_scopes hs ON hs.department_id=ea.department_id
                        WHERE ea.employee_id=r.employee_id AND ea.effective_from<=CAST(r.submitted_at AS date)
                          AND (ea.effective_to IS NULL OR ea.effective_to>CAST(r.submitted_at AS date))
                          AND hs.hr_account_id=? AND hs.revoked_at IS NULL
                          AND hs.effective_from<=SYSUTCDATETIME()
                          AND (hs.effective_to IS NULL OR hs.effective_to>SYSUTCDATETIME()) ) ))
                """;
    }

    private static void addScopeArgs(Actor a, List<Object> args) {
        if ("EMPLOYEE".equals(a.role()))
            args.add(a.employeeId());
        else if ("HR".equals(a.role())) {
            args.add(a.employeeId());
            args.add(a.accountId());
        }
    }

    public PageResponse<RequestSummary> list(AuthenticatedAccount principal, String requestType, String status,
            Long employeeId, int page, int size) {
        Actor a = actor(principal);
        type(requestType);
        if (page < 0 || size < 1 || size > 100 || (long) page * size > Integer.MAX_VALUE ||
                (employeeId != null && employeeId <= 0))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid paging or filter");
        StringBuilder where = new StringBuilder(" FROM dbo.employee_requests r WHERE r.request_type=?");
        List<Object> args = new ArrayList<>();
        args.add(requestType);
        if ("DOI_LICH".equals(requestType))
            where.append(
                    " AND EXISTS(SELECT 1 FROM dbo.schedule_change_request_details d WHERE d.request_id=r.request_id)");
        else
            where.append(
                    " AND EXISTS(SELECT 1 FROM dbo.attendance_correction_request_details d WHERE d.request_id=r.request_id)");
        if (status != null && !status.isBlank()) {
            where.append(" AND r.status=?");
            args.add(status);
        }
        if (employeeId != null) {
            where.append(" AND r.employee_id=?");
            args.add(employeeId);
        }
        where.append(scopeClause(a));
        addScopeArgs(a, args);
        Long total = db.queryForObject("SELECT COUNT_BIG(*)" + where, Long.class, args.toArray());
        List<Object> paged = new ArrayList<>(args);
        paged.add(size);
        paged.add((long) page * size);
        List<RequestSummary> rows = db.query(
                "SELECT " + FIELDS + where
                        + " ORDER BY r.submitted_at DESC,r.request_id DESC OFFSET ? ROWS FETCH NEXT ? ROWS ONLY",
                MAPPER, orderPagination(paged, args.size()).toArray());
        long n = total == null ? 0 : total;
        return new PageResponse<>(rows, page, size, n, (int) Math.min(Integer.MAX_VALUE, (n + size - 1) / size));
    }

    // SQL OFFSET comes before FETCH; reserve two bind parameters accordingly.
    private static List<Object> orderPagination(List<Object> args, int position) {
        Object limit = args.get(position), offset = args.get(position + 1);
        args.set(position, offset);
        args.set(position + 1, limit);
        return args;
    }

    public RequestSummary get(AuthenticatedAccount principal, String requestType, long id) {
        Actor a = actor(principal);
        type(requestType);
        if (id <= 0)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid request ID");
        StringBuilder where = new StringBuilder(
                " FROM dbo.employee_requests r WHERE r.request_id=? AND r.request_type=?");
        List<Object> args = new ArrayList<>();
        args.add(id);
        args.add(requestType);
        where.append(scopeClause(a));
        addScopeArgs(a, args);
        List<RequestSummary> rows = db.query("SELECT " + FIELDS + where, MAPPER, args.toArray());
        if (rows.size() != 1)
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Request not found");
        return rows.get(0);
    }
}
