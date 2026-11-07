package com.hrm.controller;

import com.hrm.security.AuthenticatedAccount;
import com.hrm.service.ScheduleQueryService;
import com.hrm.service.ScheduleStateService;
import com.hrm.service.ScheduleStateWriteService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/management/schedules")
public class ScheduleStateController {
    public record ReasonBody(String reason) {
    }

    public record StateResponse(long scheduleId, String state) {
    }

    private final ScheduleQueryService queries;
    private final ScheduleStateService states;
    private final ScheduleStateWriteService writes;

    public ScheduleStateController(ScheduleQueryService queries, ScheduleStateService states,
            ScheduleStateWriteService writes) {
        this.queries = queries;
        this.states = states;
        this.writes = writes;
    }

    @GetMapping("/{id}/state")
    public StateResponse state(@AuthenticationPrincipal AuthenticatedAccount actor, @PathVariable long id) {
        queries.getAuthorized(actor, id);
        return new StateResponse(id, states.current(id).name());
    }

    @PostMapping("/{id}/lock-edit")
    public StateResponse lock(@AuthenticationPrincipal AuthenticatedAccount actor, @PathVariable long id,
            @RequestBody ReasonBody request) {
        return new StateResponse(id, writes.lockEdit(actor, id, request == null ? null : request.reason()).name());
    }

    @PostMapping("/{id}/unlock-edit")
    public StateResponse unlock(@AuthenticationPrincipal AuthenticatedAccount actor, @PathVariable long id,
            @RequestBody ReasonBody request) {
        return new StateResponse(id, writes.unlockEdit(actor, id, request == null ? null : request.reason()).name());
    }

    @PostMapping("/{id}/cancel")
    public StateResponse cancel(@AuthenticationPrincipal AuthenticatedAccount actor, @PathVariable long id,
            @RequestBody ReasonBody request) {
        return new StateResponse(id, writes.cancel(actor, id, request == null ? null : request.reason()).name());
    }
}
