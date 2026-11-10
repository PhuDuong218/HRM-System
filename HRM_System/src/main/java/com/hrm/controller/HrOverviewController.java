package com.hrm.controller;

import com.hrm.security.AuthenticatedAccount;
import com.hrm.service.HrOverviewService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/overview")
public class HrOverviewController {
    private final HrOverviewService service;

    public HrOverviewController(HrOverviewService service) { this.service = service; }

    @GetMapping("/hr")
    @PreAuthorize("hasRole('HR')")
    public HrOverviewService.Overview overview(@AuthenticationPrincipal AuthenticatedAccount actor) {
        return service.get(actor);
    }
}
