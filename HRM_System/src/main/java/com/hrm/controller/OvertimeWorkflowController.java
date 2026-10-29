package com.hrm.controller;

import com.hrm.security.AuthenticatedAccount;
import com.hrm.service.OvertimeRequestWorkflowService;
import com.hrm.service.OvertimeRequestWorkflowService.Window;
import com.hrm.service.OvertimeRequestWorkflowService.DecisionWindow;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.Map;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/overtime/workflows")
@ConditionalOnProperty(prefix = "hrm.task", name = "api-enabled", havingValue = "true", matchIfMissing = false)
public class OvertimeWorkflowController {
    private final OvertimeRequestWorkflowService service;

    public OvertimeWorkflowController(OvertimeRequestWorkflowService service) {
        this.service = service;
    }

    public record Reason(@NotBlank String reason) {
    }

    public record OverflowSubmission(@NotBlank String reason, @NotNull List<Window> windows) {
    }

    public record Decision(boolean approve, String rejectionReason, List<DecisionWindow> accepted) {
    }

    @PostMapping("/ot/{id}/cancel-request")
    public long cancel(@AuthenticationPrincipal AuthenticatedAccount a, @PathVariable long id,
            @Valid @RequestBody Reason r) {
        return service.requestCancellation(a, id, r.reason());
    }

    @PostMapping("/ot/{id}/overflow-request")
    public long overflow(@AuthenticationPrincipal AuthenticatedAccount a, @PathVariable long id,
            @Valid @RequestBody OverflowSubmission r) {
        return service.requestOverflow(a, id, r.reason(), r.windows());
    }

    @GetMapping("/requests/{requestId}/intervals")
    public List<Map<String, Object>> intervals(@AuthenticationPrincipal AuthenticatedAccount a,
            @PathVariable long requestId) {
        return service.intervals(a, requestId);
    }

    @PostMapping("/requests/{requestId}/decision")
    public void decide(@AuthenticationPrincipal AuthenticatedAccount a, @PathVariable long requestId,
            @RequestBody Decision d) {
        service.decide(a, requestId, d.approve(), d.rejectionReason(), d.accepted());
    }
}
