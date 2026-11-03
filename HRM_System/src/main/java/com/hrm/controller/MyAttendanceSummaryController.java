package com.hrm.controller;
import com.hrm.security.AuthenticatedAccount;
import com.hrm.service.ConfirmedAttendancePeriodService;
import java.time.LocalDate;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
public class MyAttendanceSummaryController {
    private final ConfirmedAttendancePeriodService service;
    public MyAttendanceSummaryController(ConfirmedAttendancePeriodService service) { this.service = service; }
    @GetMapping("/api/me/attendance-summary")
    public ConfirmedAttendancePeriodService.PeriodSummary mine(@AuthenticationPrincipal AuthenticatedAccount actor,
            @RequestParam LocalDate from, @RequestParam LocalDate to) {
        return service.summarizeMine(actor, from, to);
    }
}
