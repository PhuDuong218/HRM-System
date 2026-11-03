package com.hrm.controller;

import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import com.hrm.security.AuthenticatedAccount;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.*;
import jakarta.validation.Valid;
import com.hrm.service.WorkShiftService;
import com.hrm.service.DetailedWorkTimeService;
import com.hrm.dto.DetailedWorkTimeDTOs.ShiftWithBreaks;
import static com.hrm.dto.WorkTimeDTOs.*;

/** Disabled by default until Spring Security/JWT authorization is available. */
@RestController
@RequestMapping("/api/management/shifts")
@ConditionalOnProperty(prefix = "hrm.task", name = "api-enabled", havingValue = "true", matchIfMissing = false)
public class WorkShiftController {
    private final WorkShiftService shifts;
    private final DetailedWorkTimeService detailed;
    private final com.hrm.service.WorkShiftManagementWriteService management;
    private final com.hrm.service.WorkShiftPermissionService permissions;

    public WorkShiftController(WorkShiftService shifts, DetailedWorkTimeService detailed,
            com.hrm.service.WorkShiftManagementWriteService management,
            com.hrm.service.WorkShiftPermissionService permissions) {
        this.shifts = shifts;
        this.detailed = detailed;
        this.management = management;
        this.permissions = permissions;
    }

    @GetMapping
    public List<ShiftView> list(@AuthenticationPrincipal AuthenticatedAccount actor) {
        permissions.requireView(actor);
        return shifts.list();
    }

    @GetMapping("/{id}")
    public ShiftView get(@AuthenticationPrincipal AuthenticatedAccount actor, @PathVariable long id) {
        permissions.requireView(actor);
        return shifts.get(id);
    }

    @PostMapping
    public ShiftView create(@AuthenticationPrincipal AuthenticatedAccount actor,
            @Valid @RequestBody ShiftWithBreaks body) {
        return management.create(actor, body);
    }

    @PutMapping("/{id}")
    public ShiftView update(@AuthenticationPrincipal AuthenticatedAccount actor, @PathVariable long id,
            @Valid @RequestBody ShiftWithBreaks body) {
        return management.update(actor, id, body);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@AuthenticationPrincipal AuthenticatedAccount actor,
            @PathVariable long id) {
        shifts.deleteOrDeactivate(actor, id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/capabilities")
    public java.util.Map<String, Boolean> capabilities(@AuthenticationPrincipal AuthenticatedAccount actor) {
        try {
            permissions.requireManage(actor);
            return java.util.Map.of("canManage", true);
        } catch (org.springframework.web.server.ResponseStatusException denied) {
            if (denied.getStatusCode().value() != 403) throw denied;
            return java.util.Map.of("canManage", false);
        }
    }

    @PatchMapping("/{id}/deactivate")
    public ShiftView deactivate(@AuthenticationPrincipal AuthenticatedAccount actor, @PathVariable long id,
            @Valid @RequestBody WorkShiftManagementWriteController.ReasonRequest request) {
        return management.setActive(actor, id, false, request.reason());
    }

    @PatchMapping("/{id}/activate")
    public ShiftView activate(@AuthenticationPrincipal AuthenticatedAccount actor, @PathVariable long id,
            @Valid @RequestBody WorkShiftManagementWriteController.ReasonRequest request) {
        return management.setActive(actor, id, true, request.reason());
    }
}
