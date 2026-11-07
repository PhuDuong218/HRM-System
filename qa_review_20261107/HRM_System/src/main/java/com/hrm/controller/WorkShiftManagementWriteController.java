package com.hrm.controller;

import com.hrm.dto.DetailedWorkTimeDTOs.ShiftWithBreaks;
import com.hrm.dto.WorkTimeDTOs.ShiftView;
import com.hrm.security.AuthenticatedAccount;
import com.hrm.service.WorkShiftManagementWriteService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/** Dedicated endpoints while legacy task APIs remain disabled. */
@RestController
@RequestMapping("/api/management/shifts")
@ConditionalOnProperty(prefix = "hrm.task", name = "api-enabled", havingValue = "false", matchIfMissing = true)
public class WorkShiftManagementWriteController {
    private final WorkShiftManagementWriteService service;
    private final com.hrm.service.WorkShiftPermissionService permissions;

    public WorkShiftManagementWriteController(WorkShiftManagementWriteService service,
            com.hrm.service.WorkShiftPermissionService permissions) {
        this.service = service;
        this.permissions = permissions;
    }

    public record ReasonRequest(@NotBlank String reason) {
    }

    @PostMapping
    public ShiftView create(@AuthenticationPrincipal AuthenticatedAccount actor,
            @Valid @RequestBody ShiftWithBreaks input) {
        return service.create(actor, input);
    }

    @PutMapping("/{id}")
    public ShiftView update(@AuthenticationPrincipal AuthenticatedAccount actor,
            @PathVariable long id, @Valid @RequestBody ShiftWithBreaks input) {
        return service.update(actor, id, input);
    }

    public record BasicInfoRequest(@NotBlank String shiftName) {
    }

    @PatchMapping("/{id}/basic-info")
    public ShiftView updateBasicInfo(@AuthenticationPrincipal AuthenticatedAccount actor,
            @PathVariable long id, @Valid @RequestBody BasicInfoRequest input) {
        return service.updateBasicInfo(actor, id, input.shiftName());
    }

    @PatchMapping("/{id}/deactivate")
    public ShiftView deactivate(@AuthenticationPrincipal AuthenticatedAccount actor,
            @PathVariable long id, @Valid @RequestBody ReasonRequest input) {
        return service.setActive(actor, id, false, input.reason());
    }

    @PatchMapping("/{id}/activate")
    public ShiftView activate(@AuthenticationPrincipal AuthenticatedAccount actor,
            @PathVariable long id, @Valid @RequestBody ReasonRequest input) {
        return service.setActive(actor, id, true, input.reason());
    }
}
