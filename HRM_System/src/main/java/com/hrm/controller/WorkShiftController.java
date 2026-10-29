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

    public WorkShiftController(WorkShiftService shifts, DetailedWorkTimeService detailed) {
        this.shifts = shifts;
        this.detailed = detailed;
    }

    @GetMapping
    public List<ShiftView> list() {
        return shifts.list();
    }

    @GetMapping("/{id}")
    public ShiftView get(@PathVariable long id) {
        return shifts.get(id);
    }

    @PostMapping
    public ShiftView create(@AuthenticationPrincipal AuthenticatedAccount actor,
            @Valid @RequestBody ShiftWithBreaks body) {
        return detailed.createShift(actor, body);
    }

    @PutMapping("/{id}")
    public ShiftView update(@AuthenticationPrincipal AuthenticatedAccount actor, @PathVariable long id,
            @Valid @RequestBody ShiftWithBreaks body) {
        return detailed.updateShift(actor, id, body);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@AuthenticationPrincipal AuthenticatedAccount actor,
            @PathVariable long id) {
        shifts.deleteOrDeactivate(actor, id);
        return ResponseEntity.noContent().build();
    }
}
