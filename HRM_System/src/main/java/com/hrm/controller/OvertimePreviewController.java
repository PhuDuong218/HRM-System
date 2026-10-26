package com.hrm.controller;

import com.hrm.dto.OvertimeDTOs.BreakInput;
import com.hrm.security.AuthenticatedAccount;
import com.hrm.service.OvertimePreviewService;
import java.util.List;
import jakarta.validation.Valid;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/** Read-only preview, does not create/approve/confirm OT or change SQL data. */
@RestController
@RequestMapping("/api/overtime")
@ConditionalOnProperty(prefix = "hrm.task", name = "api-enabled", havingValue = "true", matchIfMissing = false)
public class OvertimePreviewController {
    private final OvertimePreviewService service;

    public OvertimePreviewController(OvertimePreviewService service) {
        this.service = service;
    }

    public record PreviewRequest(@Valid List<BreakInput> breaks) {
    }

    @PostMapping("/requests/{id}/preview")
    public OvertimePreviewService.Preview preview(@AuthenticationPrincipal AuthenticatedAccount principal,
            @PathVariable long id, @Valid @RequestBody PreviewRequest request) {
        return service.preview(principal, id, request.breaks());
    }
}
