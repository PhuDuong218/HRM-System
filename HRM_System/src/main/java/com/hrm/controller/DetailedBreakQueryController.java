package com.hrm.controller;

import com.hrm.dto.DetailedWorkTimeDTOs.OffsetBreak;
import com.hrm.dto.DetailedWorkTimeDTOs.TimedBreak;
import com.hrm.security.AuthenticatedAccount;
import com.hrm.service.DetailedWorkTimeService;
import com.hrm.service.ScheduleQueryService;
import com.hrm.service.WorkShiftPermissionService;
import com.hrm.service.WorkShiftService;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * Read-only detailed breaks with the same live authorization checks as existing
 * APIs.
 */
@RestController
@RequestMapping("/api/management/work-time-details")
@ConditionalOnProperty(prefix = "hrm.task", name = "api-enabled", havingValue = "true", matchIfMissing = false)
public class DetailedBreakQueryController {
    public record ShiftBreaks(long shiftId, List<OffsetBreak> breaks) {
    }

    public record ScheduleBreaks(long scheduleId, String breakDetailStatus, List<TimedBreak> breaks) {
    }

    private final DetailedWorkTimeService details;
    private final WorkShiftService shifts;
    private final WorkShiftPermissionService shiftPermissions;
    private final ScheduleQueryService scheduleQueries;
    private final JdbcTemplate db;

    public DetailedBreakQueryController(DetailedWorkTimeService details, WorkShiftService shifts,
            WorkShiftPermissionService shiftPermissions, ScheduleQueryService scheduleQueries, JdbcTemplate db) {
        this.details = details;
        this.shifts = shifts;
        this.shiftPermissions = shiftPermissions;
        this.scheduleQueries = scheduleQueries;
        this.db = db;
    }

    /** Restricted to active HR/ADMIN shift managers, not an anonymous lookup. */
    @GetMapping("/shifts/{id}/breaks")
    public ShiftBreaks shiftBreaks(@AuthenticationPrincipal AuthenticatedAccount actor,
            @PathVariable long id) {
        shiftPermissions.requireManage(actor);
        shifts.get(id); // Reject unknown shift before returning detail rows.
        return new ShiftBreaks(id, details.shiftBreaks(id));
    }

    /** Employee sees own schedule; HR sees scoped schedule; ADMIN sees all. */
    @GetMapping("/schedules/{id}/breaks")
    public ScheduleBreaks scheduleBreaks(@AuthenticationPrincipal AuthenticatedAccount actor,
            @PathVariable long id) {
        scheduleQueries.getAuthorized(actor, id);
        List<String> statuses = db.query(
                "SELECT break_detail_status FROM dbo.work_schedules WHERE schedule_id=?",
                (rs, rowNum) -> rs.getString(1), id);
        if (statuses.size() != 1) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Schedule not found");
        }
        // An empty list means no detail rows. LEGACY_UNVERIFIED must never be
        // interpreted as a verified break-free schedule by consumers.
        return new ScheduleBreaks(id, statuses.get(0), details.scheduleBreaks(id));
    }
}
