package com.hrm.controller;

import com.hrm.dto.AttendanceDTOs.AttendanceCorrection;
import com.hrm.security.AuthenticatedAccount;
import com.hrm.service.AttendanceCorrectionRequestService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/** Draft endpoints; not enabled by default. */
@RestController
@RequestMapping("/api/management/attendance-correction-requests")
@ConditionalOnProperty(prefix = "hrm.task", name = "api-enabled", havingValue = "true")
public class AttendanceCorrectionRequestController {
    public record Submit(long attendanceId, AttendanceCorrection correction) {
    }

    public record Decision(boolean approve, String rejectionReason) {
    }

    public record Created(long requestId) {
    }

    private final AttendanceCorrectionRequestService service;

    public AttendanceCorrectionRequestController(AttendanceCorrectionRequestService service) {
        this.service = service;
    }

    @PostMapping
    public Created submit(@AuthenticationPrincipal AuthenticatedAccount actor, @RequestBody Submit body) {
        return new Created(service.submit(actor, body.attendanceId(), body.correction()));
    }

    @PostMapping("/{id}/decision")
    public void decide(@AuthenticationPrincipal AuthenticatedAccount actor,
            @PathVariable long id, @RequestBody Decision body) {
        service.decide(actor, id, body.approve(), body.rejectionReason());
    }
}
