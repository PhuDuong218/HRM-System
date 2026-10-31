package com.hrm.controller;

import com.hrm.security.AuthenticatedAccount;
import com.hrm.service.AttendanceManagementReadOnlyQueryService;
import java.time.LocalDate;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * Authenticated management-only GET; no writes and no dependency on task
 * write-API gate.
 */
@RestController
@RequestMapping("/api/management/attendance")
public class AttendanceManagementReadOnlyController {
    private final AttendanceManagementReadOnlyQueryService query;

    public AttendanceManagementReadOnlyController(AttendanceManagementReadOnlyQueryService query) {
        this.query = query;
    }

    @GetMapping
    public AttendanceManagementReadOnlyQueryService.Page search(
            @AuthenticationPrincipal AuthenticatedAccount actor,
            @RequestParam LocalDate from,
            @RequestParam LocalDate to,
            @RequestParam(required = false) Long employeeId,
            @RequestParam(required = false) String verificationStatus,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        if (actor == null || actor.accountId() == null || actor.accountId() <= 0)
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Login required");
        return query.search(actor.accountId(), from, to, employeeId, verificationStatus, page, size);
    }
}
