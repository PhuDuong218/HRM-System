package com.hrm.controller;

import com.hrm.dto.PageResponse;
import com.hrm.security.AuthenticatedAccount;
import com.hrm.service.WorkflowRequestQueryService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/management/workflow-requests")
@ConditionalOnProperty(prefix = "hrm.task", name = "api-enabled", havingValue = "true", matchIfMissing = false)
public class WorkflowRequestQueryController {
    private final WorkflowRequestQueryService queries;

    public WorkflowRequestQueryController(WorkflowRequestQueryService queries) {
        this.queries = queries;
    }

    @GetMapping
    public PageResponse<WorkflowRequestQueryService.RequestSummary> list(
            @AuthenticationPrincipal AuthenticatedAccount actor,
            @RequestParam String type,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Long employeeId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        return queries.list(actor, type, status, employeeId, page, size);
    }

    @GetMapping("/{id}")
    public WorkflowRequestQueryService.RequestSummary get(
            @AuthenticationPrincipal AuthenticatedAccount actor,
            @RequestParam String type, @PathVariable long id) {
        return queries.get(actor, type, id);
    }
}
