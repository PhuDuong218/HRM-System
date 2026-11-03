package com.hrm.controller;

import java.util.List;
import java.time.LocalDate;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.jdbc.core.JdbcTemplate;
import com.hrm.service.DetailedWorkTimeService;
import com.hrm.dto.DetailedWorkTimeDTOs.OffsetBreak;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.hrm.dto.WorkTimeDTOs.ShiftView;
import com.hrm.security.AuthenticatedAccount;
import com.hrm.service.WorkShiftPermissionService;
import com.hrm.service.WorkShiftService;

/**
 * Read-only management endpoints while the task write/API controller is
 * disabled.
 * When hrm.task.api-enabled=true, WorkShiftController owns these URLs instead.
 */
@RestController
@RequestMapping("/api/management/shifts")
@ConditionalOnProperty(prefix = "hrm.task", name = "api-enabled", havingValue = "false", matchIfMissing = true)
public class WorkShiftReadOnlyController {

    private final WorkShiftService shifts;
    private final JdbcTemplate db;
    private final DetailedWorkTimeService detailed;
    private final WorkShiftPermissionService permissions;

    public WorkShiftReadOnlyController(WorkShiftService shifts, WorkShiftPermissionService permissions, JdbcTemplate db,
            DetailedWorkTimeService detailed) {
        this.db = db;
        this.detailed = detailed;
        this.shifts = shifts;
        this.permissions = permissions;
    }

    @GetMapping
    public List<ShiftView> list(@AuthenticationPrincipal AuthenticatedAccount actor,
            @RequestParam(required = false) LocalDate usedFrom,
            @RequestParam(required = false) LocalDate usedTo) {
        permissions.requireView(actor);
        if (usedFrom == null && usedTo == null)
            return shifts.list();
        if (usedFrom != null && usedTo != null && usedFrom.isAfter(usedTo))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Khoảng ngày không hợp lệ");
        // Only filter templates referenced by schedules in the requested date window.
        // The shift template's status does not affect historical references.
        return db.query("""
                SELECT ws.shift_id, ws.shift_code, ws.shift_name, ws.start_time, ws.end_time,
                       ws.end_day_offset, ws.break_minutes, ws.is_active
                FROM dbo.work_shifts ws
                WHERE EXISTS (SELECT 1 FROM dbo.work_schedules s
                              WHERE s.shift_id=ws.shift_id
                                AND (? IS NULL OR s.work_date>=?)
                                AND (? IS NULL OR s.work_date<=?))
                ORDER BY ws.shift_id
                """, (r, i) -> new ShiftView(r.getLong(1), r.getString(2), r.getString(3),
                r.getTime(4).toLocalTime(), r.getTime(5).toLocalTime(),
                r.getInt(6), r.getInt(7), r.getBoolean(8)),
                usedFrom, usedFrom, usedTo, usedTo);
    }

    public record ShiftDetail(ShiftView shift, List<OffsetBreak> breaks) {
    }

    @GetMapping("/{id}/details")
    public ShiftDetail details(@AuthenticationPrincipal AuthenticatedAccount actor, @PathVariable long id) {
        permissions.requireView(actor);
        return new ShiftDetail(shifts.get(id), detailed.shiftBreaks(id));
    }

    @GetMapping("/{id}")
    public ShiftView get(@AuthenticationPrincipal AuthenticatedAccount actor, @PathVariable long id) {
        permissions.requireView(actor);
        return shifts.get(id);
    }

    @GetMapping("/capabilities")
    public java.util.Map<String, Boolean> capabilities(
            @AuthenticationPrincipal AuthenticatedAccount actor) {

        permissions.requireView(actor);

        try {
            permissions.requireManage(actor);
            return java.util.Map.of("canManage", true);
        } catch (ResponseStatusException denied) {
            if (denied.getStatusCode().value() != 403) {
                throw denied;
            }
            return java.util.Map.of("canManage", false);
        }
    }
}
