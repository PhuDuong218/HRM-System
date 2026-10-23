package com.hrm.controller;

import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.*;
import jakarta.validation.Valid;
import java.time.LocalDate;
import com.hrm.service.WorkScheduleService;
import static com.hrm.dto.WorkTimeDTOs.*;

/** Disabled by default until Spring Security/JWT authorization is available. */
@RestController
@RequestMapping("/api/management/schedules")
@ConditionalOnProperty(prefix = "hrm.task", name = "api-enabled", havingValue = "true", matchIfMissing = false)
public class WorkScheduleController {
    private final WorkScheduleService schedules;

    public WorkScheduleController(WorkScheduleService schedules) {
        this.schedules = schedules;
    }

    @GetMapping
    public List<ScheduleView> list(@RequestParam long employeeId, @RequestParam LocalDate from,
            @RequestParam LocalDate to) {
        return schedules.list(employeeId, from, to);
    }

    @GetMapping("/{id}")
    public ScheduleView get(@PathVariable long id) {
        return schedules.get(id);
    }

    @PostMapping
    public ScheduleView create(@Valid @RequestBody ScheduleInput body) {
        return schedules.create(body);
    }

    @PutMapping("/{id}")
    public ScheduleView update(@PathVariable long id, @Valid @RequestBody ScheduleInput body) {
        return schedules.update(id, body);
    }
}
