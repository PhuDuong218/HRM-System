package com.hrm.controller;

import java.util.List;

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
    private final WorkShiftPermissionService permissions;

    public WorkShiftReadOnlyController(WorkShiftService shifts, WorkShiftPermissionService permissions) {
        this.shifts = shifts;
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
}
