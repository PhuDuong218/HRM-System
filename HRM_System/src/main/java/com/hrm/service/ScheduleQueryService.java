package com.hrm.service;

import com.hrm.dto.PageResponse;
import com.hrm.dto.WorkTimeDTOs.ScheduleView;
import com.hrm.security.AuthenticatedAccount;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Read-only schedule queries. HR management scope follows active
 * department grants in dbo.hr_department_scopes.
 */
@Service
public class ScheduleQueryService {
    private static final int PAGE_SIZE = 10;
    private final JdbcTemplate db;

    public ScheduleQueryService(JdbcTemplate db) {
        this.db = db;
    }

    private static final RowMapper<ScheduleView> MAPPER = (r, n) -> new ScheduleView(
            r.getLong("schedule_id"), r.getLong("employee_id"), r.getObject("shift_id", Long.class),
            r.getDate("work_date").toLocalDate(), r.getTimestamp("planned_start").toLocalDateTime(),
            r.getTimestamp("planned_end").toLocalDateTime(), r.getInt("planned_break_minutes"), r.getString("note"));

    private static final String BASE = " FROM dbo.work_schedules s WHERE 1=1 ";
    private static final String DEPARTMENT_AT_SHIFT = """
            EXISTS (SELECT 1 FROM dbo.employee_assignments ea
                    WHERE ea.employee_id=s.employee_id AND ea.department_id=?
                      AND ea.effective_from<=CAST(s.planned_start AS DATE)
                      AND (ea.effective_to IS NULL
                           OR ea.effective_to>CAST(s.planned_start AS DATE)))
            """;
    private static final String HR_SCOPE = """
            EXISTS (
                SELECT 1
                FROM dbo.employee_assignments target
                JOIN dbo.hr_department_scopes scope
                  ON scope.department_id = target.department_id
                JOIN dbo.accounts hr_account
                  ON hr_account.account_id = scope.hr_account_id
                JOIN dbo.roles hr_role
                  ON hr_role.role_id = hr_account.role_id
                WHERE target.employee_id = s.employee_id
                  AND target.effective_from <= CAST(s.planned_start AS DATE)
                  AND (target.effective_to IS NULL
                       OR target.effective_to > CAST(s.planned_start AS DATE))
                  AND scope.hr_account_id = ?
                  AND scope.revoked_at IS NULL
                  AND scope.effective_from <= SYSUTCDATETIME()
                  AND (scope.effective_to IS NULL
                       OR scope.effective_to > SYSUTCDATETIME())
                  AND hr_account.is_active = 1
                  AND hr_role.role_code = 'HR'
            )
            """;

    private static void checkActor(AuthenticatedAccount actor) {
        if (actor == null || actor.accountId() == null ||
                actor.roleCode() == null ||
                !(actor.roleCode().equals("ADMIN") ||
                        actor.roleCode().equals("HR") || actor.roleCode().equals("EMPLOYEE"))
                ||
                (!actor.roleCode().equals("ADMIN") && actor.employeeId() == null)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Không có quyền tra cứu lịch");
        }
    }

    public PageResponse<ScheduleView> search(AuthenticatedAccount actor, Long employeeId,
            Long departmentId, LocalDate from, LocalDate to, int page, boolean personal) {
        checkActor(actor);
        if (from == null || to == null || from.isAfter(to))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Khoảng ngày không hợp lệ");
        if (page < 0 || (long) page * PAGE_SIZE > Integer.MAX_VALUE)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Phân trang không hợp lệ");
        if ((employeeId != null && employeeId <= 0) || (departmentId != null && departmentId <= 0))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "ID không hợp lệ");
        if (!personal && actor.roleCode().equals("EMPLOYEE"))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Chỉ được tra cứu lịch cá nhân");

        StringBuilder where = new StringBuilder(BASE);
        List<Object> params = new ArrayList<>();
        // work_date is the local date of planned_start, including overnight shifts.
        where.append(" AND s.work_date>=? AND s.work_date<=?");
        params.add(from);
        params.add(to);
        if (personal) {
            where.append(" AND s.employee_id=?");
            params.add(actor.employeeId());
        } else {
            if (employeeId != null) {
                where.append(" AND s.employee_id=?");
                params.add(employeeId);
            }
            if (departmentId != null) {
                where.append(" AND ").append(DEPARTMENT_AT_SHIFT);
                params.add(departmentId);
            }
            if (actor.roleCode().equals("HR")) {
                where.append(" AND ").append(HR_SCOPE);
                params.add(actor.accountId());
            }
        }
        Long total = db.queryForObject("SELECT COUNT_BIG(1)" + where, Long.class, params.toArray());
        long count = total == null ? 0 : total;
        List<Object> paged = new ArrayList<>(params);
        paged.add((long) page * PAGE_SIZE);
        paged.add(PAGE_SIZE);
        List<ScheduleView> items = db.query("SELECT s.schedule_id,s.employee_id,s.shift_id,s.work_date," +
                "s.planned_start,s.planned_end,s.planned_break_minutes,s.note" + where +
                " ORDER BY s.planned_start,s.schedule_id OFFSET ? ROWS FETCH NEXT ? ROWS ONLY",
                MAPPER, paged.toArray());
        int totalPages = (int) Math.min(Integer.MAX_VALUE, (count + PAGE_SIZE - 1) / PAGE_SIZE);
        return new PageResponse<>(items, page, PAGE_SIZE, count, totalPages);
    }

    public ScheduleView getAuthorized(AuthenticatedAccount actor, long id) {
        checkActor(actor);
        if (id <= 0)
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy lịch");
        StringBuilder where = new StringBuilder(BASE).append(" AND s.schedule_id=?");
        List<Object> params = new ArrayList<>();
        params.add(id);
        if (actor.roleCode().equals("EMPLOYEE")) {
            where.append(" AND s.employee_id=?");
            params.add(actor.employeeId());
        } else if (actor.roleCode().equals("HR")) {
            where.append(" AND ").append(HR_SCOPE);
            params.add(actor.accountId());
        }
        return db.query("SELECT s.schedule_id,s.employee_id,s.shift_id,s.work_date," +
                "s.planned_start,s.planned_end,s.planned_break_minutes,s.note" + where,
                MAPPER, params.toArray()).stream().findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy lịch"));
    }
}
