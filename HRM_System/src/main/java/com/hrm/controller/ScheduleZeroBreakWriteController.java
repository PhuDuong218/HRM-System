package com.hrm.controller;

import com.hrm.dto.WorkTimeDTOs.ScheduleInput;
import com.hrm.dto.WorkTimeDTOs.ScheduleView;
import com.hrm.security.AuthenticatedAccount;
import com.hrm.service.ScheduleZeroBreakWriteService;
import jakarta.validation.Valid;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/** Dedicated writes without enabling every legacy hrm.task API. */
@RestController
@RequestMapping("/api/management/schedules")
@ConditionalOnProperty(prefix = "hrm.task", name = "api-enabled", havingValue = "false", matchIfMissing = true)
public class ScheduleZeroBreakWriteController {
    private final ScheduleZeroBreakWriteService service;

    public ScheduleZeroBreakWriteController(ScheduleZeroBreakWriteService service) {
        this.service = service;
    }

    @GetMapping("/capabilities")
    public java.util.Map<String, Boolean> capabilities(@AuthenticationPrincipal AuthenticatedAccount actor) {
        return java.util.Map.of("canManage", service.canManage(actor));
    }

    @PostMapping
    public ScheduleView create(@AuthenticationPrincipal AuthenticatedAccount actor,
            @Valid @RequestBody ScheduleInput input) {
        return service.create(actor, input);
    }

    @PutMapping("/{id}")
    public ScheduleView update(@AuthenticationPrincipal AuthenticatedAccount actor, @PathVariable long id,
            @Valid @RequestBody ScheduleInput input) {
        return service.update(actor, id, input);
    }
}
