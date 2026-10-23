package com.hrm.controller;

import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.*;
import jakarta.validation.Valid;
import com.hrm.service.WorkShiftService;
import static com.hrm.dto.WorkTimeDTOs.*;

/** Disabled by default until Spring Security/JWT authorization is available. */
@RestController
@RequestMapping("/api/management/shifts")
@ConditionalOnProperty(prefix = "hrm.task", name = "api-enabled", havingValue = "true", matchIfMissing = false)
public class WorkShiftController {
    private final WorkShiftService shifts;

    public WorkShiftController(WorkShiftService shifts) {
        this.shifts = shifts;
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
    public ShiftView create(@Valid @RequestBody ShiftInput body) {
        return shifts.create(body);
    }

    @PutMapping("/{id}")
    public ShiftView update(@PathVariable long id, @Valid @RequestBody ShiftInput body) {
        return shifts.update(id, body);
    }
}
