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
import com.hrm.service.WorkShiftManagementWriteService;
import com.hrm.service.WorkShiftPermissionService;
import com.hrm.dto.DetailedWorkTimeDTOs.OffsetBreak;
import jakarta.validation.constraints.NotBlank;
import com.hrm.dto.DetailedWorkTimeDTOs.ShiftWithBreaks;
import static com.hrm.dto.WorkTimeDTOs.*;

/** Disabled by default until Spring Security/JWT authorization is available. */
@RestController
@RequestMapping("/api/management/shifts")
@ConditionalOnProperty(prefix = "hrm.task", name = "api-enabled", havingValue = "true", matchIfMissing = false)
public class WorkShiftController {
    private final WorkShiftService shifts;
    private final DetailedWorkTimeService detailed;
    private final WorkShiftManagementWriteService management;
    private final WorkShiftPermissionService permissions;

    public WorkShiftController(WorkShiftService shifts, DetailedWorkTimeService detailed,
            WorkShiftManagementWriteService management, WorkShiftPermissionService permissions) {
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

    public record ShiftDetail(ShiftView shift, List<OffsetBreak> breaks) {}

    @GetMapping("/{id}/details")
    public ShiftDetail details(@AuthenticationPrincipal AuthenticatedAccount actor, @PathVariable long id) {
        permissions.requireView(actor);
        return new ShiftDetail(shifts.get(id), detailed.shiftBreaks(id));
    }

    public record ReasonRequest(@NotBlank String reason) {}

    @PatchMapping("/{id}/deactivate")
    public ShiftView deactivate(@AuthenticationPrincipal AuthenticatedAccount actor, @PathVariable long id,
            @Valid @RequestBody ReasonRequest body) {
        return management.setActive(actor, id, false, body.reason());
    }

    @PatchMapping("/{id}/activate")
    public ShiftView activate(@AuthenticationPrincipal AuthenticatedAccount actor, @PathVariable long id,
            @Valid @RequestBody ReasonRequest body) {
        return management.setActive(actor, id, true, body.reason());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@AuthenticationPrincipal AuthenticatedAccount actor,
            @PathVariable long id) {
        shifts.deleteOrDeactivate(actor, id);
        return ResponseEntity.noContent().build();
    }
}
