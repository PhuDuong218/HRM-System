package com.hrm.controller;

import java.time.LocalDate;
import java.util.List;

import com.hrm.security.AuthenticatedAccount;
import com.hrm.service.AttendanceSelfHistoryQueryService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * Optional authenticated, read-only endpoint; no client-provided employee ID.
 */
@RestController
@RequestMapping("/api/attendance/self/history")
@ConditionalOnProperty(prefix = "hrm.task", name = "api-enabled", havingValue = "true", matchIfMissing = false)
public class AttendanceSelfHistoryController {
    private final AttendanceSelfHistoryQueryService history;

    public AttendanceSelfHistoryController(AttendanceSelfHistoryQueryService history) {
        this.history = history;
    }

    @GetMapping
    public List<AttendanceSelfHistoryQueryService.HistoryItem> mine(
            @AuthenticationPrincipal AuthenticatedAccount principal,
            @RequestParam LocalDate from, @RequestParam LocalDate to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        if (principal == null || principal.accountId() == null || principal.accountId() <= 0) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Login required");
        }
        return history.findMine(principal.accountId(), from, to, page, size);
    }
}
