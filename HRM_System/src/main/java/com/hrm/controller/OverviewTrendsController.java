package com.hrm.controller;

import com.hrm.security.AuthenticatedAccount;
import com.hrm.service.OverviewTrendsService;
import java.util.List;
import java.time.LocalDate;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class OverviewTrendsController {
    private final OverviewTrendsService service;

    public OverviewTrendsController(OverviewTrendsService service) {
        this.service = service;
    }

    @GetMapping("/api/overview/trends")
    @PreAuthorize("hasAnyRole('ADMIN','HR')")
    public ResponseEntity<List<OverviewTrendsService.Month>> trends(
            @AuthenticationPrincipal AuthenticatedAccount actor, @RequestParam(required = false) LocalDate date) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.get(actor, date));
    }
}
