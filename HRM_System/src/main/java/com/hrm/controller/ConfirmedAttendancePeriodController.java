package com.hrm.controller;

import com.hrm.security.AuthenticatedAccount;
import com.hrm.service.ConfirmedAttendancePeriodService;
import java.time.LocalDate;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/management/attendance/confirmed-summary")
@ConditionalOnProperty(prefix = "hrm.task", name = "api-enabled", havingValue = "true", matchIfMissing = false)
public class ConfirmedAttendancePeriodController {
    private final ConfirmedAttendancePeriodService service;

    public ConfirmedAttendancePeriodController(ConfirmedAttendancePeriodService service) {
        this.service = service;
    }

    @GetMapping
    public ConfirmedAttendancePeriodService.PeriodSummary get(
            @AuthenticationPrincipal AuthenticatedAccount actor,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) Long employeeId) {
        return service.summarize(actor, from, to, employeeId);
    }
}
