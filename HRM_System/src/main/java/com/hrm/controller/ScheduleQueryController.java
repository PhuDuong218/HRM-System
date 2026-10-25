package com.hrm.controller;

import com.hrm.dto.PageResponse;
import com.hrm.dto.WorkTimeDTOs.ScheduleView;
import com.hrm.security.AuthenticatedAccount;
import com.hrm.service.ScheduleQueryService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import java.time.LocalDate;

@RestController
public class ScheduleQueryController {
    private final ScheduleQueryService queries;
    public ScheduleQueryController(ScheduleQueryService queries) { this.queries = queries; }

    @GetMapping("/api/me/schedules")
    public PageResponse<ScheduleView> mine(@AuthenticationPrincipal AuthenticatedAccount actor,
            @RequestParam LocalDate from, @RequestParam LocalDate to,
            @RequestParam(defaultValue = "0") int page) {
        return queries.search(actor, null, null, from, to, page, true);
    }

    @GetMapping("/api/management/schedules/search")
    public PageResponse<ScheduleView> management(@AuthenticationPrincipal AuthenticatedAccount actor,
            @RequestParam(required = false) Long employeeId,
            @RequestParam(required = false) Long departmentId,
            @RequestParam LocalDate from, @RequestParam LocalDate to,
            @RequestParam(defaultValue = "0") int page) {
        return queries.search(actor, employeeId, departmentId, from, to, page, false);
    }
}
