package com.hrm.controller;

import com.hrm.security.AuthenticatedAccount;
import com.hrm.service.OverviewAttendanceService;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class OverviewAttendanceController {
    private final OverviewAttendanceService service;

    public OverviewAttendanceController(OverviewAttendanceService service) {
        this.service = service;
    }

    @GetMapping("/api/overview/attendance-status")
    @PreAuthorize("hasAnyRole('ADMIN','HR')")
    public ResponseEntity<OverviewAttendanceService.Status> status(@AuthenticationPrincipal AuthenticatedAccount actor) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.get(actor));
    }
}
