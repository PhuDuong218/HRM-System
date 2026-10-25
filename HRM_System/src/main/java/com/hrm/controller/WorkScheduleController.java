package com.hrm.controller;

import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.*;
import jakarta.validation.Valid;
import java.time.LocalDate;
import com.hrm.security.AuthenticatedAccount;
import com.hrm.service.ScheduleQueryService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import com.hrm.service.WorkScheduleService;
import static com.hrm.dto.WorkTimeDTOs.*;

/** Disabled by default until Spring Security/JWT authorization is available. */
@RestController
@RequestMapping("/api/management/schedules")
@ConditionalOnProperty(prefix = "hrm.task", name = "api-enabled", havingValue = "true", matchIfMissing = false)
public class WorkScheduleController {
    private final WorkScheduleService schedules;
    private final ScheduleQueryService queries;

    public WorkScheduleController(WorkScheduleService schedules, ScheduleQueryService queries) {
        this.schedules = schedules;
        this.queries = queries;
    }

    @GetMapping
    public List<ScheduleView> list(@AuthenticationPrincipal AuthenticatedAccount actor,
            @RequestParam long employeeId, @RequestParam LocalDate from,
            @RequestParam LocalDate to) {
        var first = queries.search(actor, employeeId, null, from, to, 0, false);
        if (first.totalElements() > 10) {
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.BAD_REQUEST,
                    "Kết quả vượt 10 bản ghi. Sử dụng API /search có phân trang");
        }
        return first.items();
    }

    @GetMapping("/{id}")
    public ScheduleView get(@AuthenticationPrincipal AuthenticatedAccount actor, @PathVariable long id) {
        return queries.getAuthorized(actor, id);
    }

    @PostMapping
    public ScheduleView create(@AuthenticationPrincipal AuthenticatedAccount actor,
            @Valid @RequestBody ScheduleInput body) {
        return schedules.create(actor, body);
    }

    @PutMapping("/{id}")
    public ScheduleView update(@AuthenticationPrincipal AuthenticatedAccount actor,
            @PathVariable long id, @Valid @RequestBody ScheduleInput body) {
        return schedules.update(actor, id, body);
    }
}
