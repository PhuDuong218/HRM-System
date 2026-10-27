package com.hrm.controller;

import com.hrm.dto.ScheduleChangeDTOs.Proposal;
import com.hrm.security.AuthenticatedAccount;
import com.hrm.service.ScheduleChangeRequestService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/**
 * API draft; disabled unless existing management API feature flag is enabled.
 */
@RestController
@RequestMapping("/api/management/schedule-change-requests")
@ConditionalOnProperty(prefix = "hrm.task", name = "api-enabled", havingValue = "true")
public class ScheduleChangeRequestController {
    public record Decision(boolean approve, String rejectionReason) {
    }

    public record Created(long requestId) {
    }

    private final ScheduleChangeRequestService service;

    public ScheduleChangeRequestController(ScheduleChangeRequestService service) {
        this.service = service;
    }

    @PostMapping
    public Created submit(@AuthenticationPrincipal AuthenticatedAccount actor,
            @RequestBody Proposal body) {
        return new Created(service.submit(actor, body));
    }

    @PostMapping("/{id}/decision")
    public void decide(@AuthenticationPrincipal AuthenticatedAccount actor,
            @PathVariable long id, @RequestBody Decision decision) {
        service.decide(actor, id, decision.approve(), decision.rejectionReason());
    }
}
