package com.hrm.controller;

import com.hrm.dto.DetailedWorkTimeDTOs.*;
import com.hrm.dto.WorkTimeDTOs.*;
import com.hrm.security.AuthenticatedAccount;
import com.hrm.service.DetailedWorkTimeService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/** Separate opt-in V2 routes; original controllers remain untouched. */
@RestController
@RequestMapping("/api/management/work-time-details")
@ConditionalOnProperty(prefix = "hrm.task", name = "api-enabled", havingValue = "true", matchIfMissing = false)
public class DetailedWorkTimeController {
    private final DetailedWorkTimeService service;

    public DetailedWorkTimeController(DetailedWorkTimeService service) {
        this.service = service;
    }

    @PostMapping("/shifts")
    public ShiftView createShift(@AuthenticationPrincipal AuthenticatedAccount actor,
            @Valid @RequestBody ShiftWithBreaks body) {
        return service.createShift(actor, body);
    }

    @PutMapping("/shifts/{id}")
    public ShiftView updateShift(@AuthenticationPrincipal AuthenticatedAccount actor, @PathVariable long id,
            @Valid @RequestBody ShiftWithBreaks body) {
        return service.updateShift(actor, id, body);
    }

    @PostMapping("/schedules")
    public ScheduleView createSchedule(@AuthenticationPrincipal AuthenticatedAccount actor,
            @Valid @RequestBody ScheduleWithBreaks body) {
        return service.createSchedule(actor, body);
    }

    @PutMapping("/schedules/{id}")
    public ScheduleView updateSchedule(@AuthenticationPrincipal AuthenticatedAccount actor, @PathVariable long id,
            @Valid @RequestBody ScheduleWithBreaks body) {
        return service.updateSchedule(actor, id, body);
    }
    // Reads here rely on original actor-authorization service being wired at API
    // level before exposure.
}
