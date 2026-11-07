package com.hrm.controller;

import com.hrm.security.AuthenticatedAccount;
import com.hrm.service.AttendanceSelfClockService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

/** Endpoints opt in with the existing hrm.task.api-enabled deployment gate. */
@RestController
@RequestMapping("/api/attendance/self")
@ConditionalOnProperty(prefix = "hrm.task", name = "api-enabled", havingValue = "true", matchIfMissing = false)
public class AttendanceSelfClockController {
    private final AttendanceSelfClockService service;

    public AttendanceSelfClockController(AttendanceSelfClockService service) {
        this.service = service;
    }

    private long account(AuthenticatedAccount principal) {
        if (principal == null || principal.accountId() == null || principal.accountId() <= 0)
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Login required");
        return principal.accountId();
    }

    @PostMapping("/{scheduleId}/check-in")
    public AttendanceSelfClockService.ClockEvent checkIn(@AuthenticationPrincipal AuthenticatedAccount principal,
            @PathVariable long scheduleId) {
        return service.checkIn(account(principal), scheduleId);
    }

    @PostMapping("/{scheduleId}/check-out")
    public AttendanceSelfClockService.ClockEvent checkOut(@AuthenticationPrincipal AuthenticatedAccount principal,
            @PathVariable long scheduleId) {
        return service.checkOut(account(principal), scheduleId);
    }
}
