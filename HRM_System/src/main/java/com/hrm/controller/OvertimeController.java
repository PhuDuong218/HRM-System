package com.hrm.controller;

import static com.hrm.dto.OvertimeDTOs.*;

import com.hrm.security.AuthenticatedAccount;
import com.hrm.service.OvertimeService;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/**
 * Disabled unless hrm.task.api-enabled=true. JWT authentication required.
 * Authorization is enforced in the service layer (backend), never by the UI.
 */
@RestController
@RequestMapping("/api/overtime")
@ConditionalOnProperty(prefix = "hrm.task", name = "api-enabled", havingValue = "true", matchIfMissing = false)
public class OvertimeController {
    private final OvertimeService service;

    public OvertimeController(OvertimeService service) {
        this.service = service;
    }

    @PostMapping("/requests")
    public OvertimeView register(@AuthenticationPrincipal AuthenticatedAccount a,
            @Valid @RequestBody Register body) {
        return service.register(a, body);
    }

    @GetMapping("/requests")
    public List<OvertimeView> list(@AuthenticationPrincipal AuthenticatedAccount a,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        return service.list(a, status, from, to, page, size);
    }

    @GetMapping("/requests/{id}")
    public OvertimeDetail get(@AuthenticationPrincipal AuthenticatedAccount a,
            @PathVariable long id) {
        return service.get(a, id);
    }

    @PutMapping("/requests/{id}")
    public OvertimeView update(@AuthenticationPrincipal AuthenticatedAccount a,
            @PathVariable long id, @Valid @RequestBody Register body) {
        return service.update(a, id, body);
    }

    @PostMapping("/requests/{id}/cancel")
    public OvertimeView cancel(@AuthenticationPrincipal AuthenticatedAccount a,
            @PathVariable long id, @Valid @RequestBody Cancel body) {
        return service.cancel(a, id, body.reason());
    }

    @PostMapping("/requests/{id}/approve")
    public OvertimeView approve(@AuthenticationPrincipal AuthenticatedAccount a,
            @PathVariable long id) {
        return service.decide(a, id, true, null);
    }

    @PostMapping("/requests/{id}/reject")
    public OvertimeView reject(@AuthenticationPrincipal AuthenticatedAccount a,
            @PathVariable long id, @Valid @RequestBody Reject body) {
        return service.decide(a, id, false, body.reason());
    }

    @PutMapping("/requests/{id}/actual")
    public OvertimeView actual(@AuthenticationPrincipal AuthenticatedAccount a,
            @PathVariable long id, @Valid @RequestBody Actual body) {
        return service.recordActual(a, id, body);
    }

    @PostMapping("/requests/{id}/confirm")
    public OvertimeView confirm(@AuthenticationPrincipal AuthenticatedAccount a,
            @PathVariable long id) {
        return service.confirm(a, id);
    }

    @PostMapping("/requests/{id}/reopen-confirmation")
    public OvertimeView reopenConfirmation(@AuthenticationPrincipal AuthenticatedAccount a,
            @PathVariable long id, @Valid @RequestBody ReopenConfirmation body) {
        return service.reopenConfirmation(a, id, body.reason());
    }

    @PostMapping("/requests/{id}/overflow-review")
    public OvertimeView reviewOverflow(@AuthenticationPrincipal AuthenticatedAccount a,
            @PathVariable long id, @Valid @RequestBody OverflowReview body) {
        return service.resolveOverflow(a, id, body.note());
    }
}
