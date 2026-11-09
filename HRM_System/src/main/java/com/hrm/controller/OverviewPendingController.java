package com.hrm.controller;

import com.hrm.security.AuthenticatedAccount;
import com.hrm.service.OverviewPendingService;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/overview")
public class OverviewPendingController {
    private final OverviewPendingService service;
    public OverviewPendingController(OverviewPendingService service){this.service=service;}
    @GetMapping("/pending")
    @PreAuthorize("hasAnyRole('ADMIN','HR')")
    public ResponseEntity<OverviewPendingService.Pending> pending(@AuthenticationPrincipal AuthenticatedAccount actor) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.get(actor));
    }
}
