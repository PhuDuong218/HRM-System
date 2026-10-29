package com.hrm.controller;

import com.hrm.dto.AttendanceDTOs.AttendanceCorrection;
import com.hrm.dto.AttendanceDTOs.AttendanceInput;
import com.hrm.dto.AttendanceDTOs.AttendanceView;
import com.hrm.security.AuthenticatedAccount;
import com.hrm.service.AttendanceService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * Authenticated management endpoints for attendance.
 *
 * Disabled by default, consistent with WorkScheduleController. Enabling
 * hrm.task.api-enabled must be an explicit deployment decision.
 * The Service remains the source of truth for permissions and HR scope.
 */
@RestController
@RequestMapping("/api/attendance")
@ConditionalOnProperty(prefix = "hrm.task", name = "api-enabled", havingValue = "true", matchIfMissing = false)
public class AttendanceController {

    private final AttendanceService attendanceService;

    public AttendanceController(AttendanceService attendanceService) {
        this.attendanceService = attendanceService;
    }

    private static long trustedActorId(AuthenticatedAccount actor) {
        if (actor == null || actor.accountId() == null || actor.accountId() <= 0) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Yêu cầu đăng nhập hợp lệ");
        }
        return actor.accountId();
    }

    @PostMapping("/manual")
    public AttendanceView createManual(
            @AuthenticationPrincipal AuthenticatedAccount actor,
            @Valid @RequestBody AttendanceInput body) {
        return attendanceService.createManual(body, trustedActorId(actor));
    }

    @PutMapping("/{id}/correct")
    public AttendanceView correct(
            @AuthenticationPrincipal AuthenticatedAccount actor,
            @PathVariable long id,
            @Valid @RequestBody AttendanceCorrection body) {
        return attendanceService.correct(id, body, trustedActorId(actor));
    }

    @PostMapping("/{id}/confirm")
    public AttendanceView confirm(
            @AuthenticationPrincipal AuthenticatedAccount actor,
            @PathVariable long id) {
        return attendanceService.confirm(id, trustedActorId(actor));
    }

    /**
     * The authenticated principal supplies identity; only the reason is client
     * input.
     */
    public record UnconfirmRequest(@NotBlank String reason) {
    }

    @PostMapping("/{id}/unconfirm")
    public AttendanceView unconfirm(
            @AuthenticationPrincipal AuthenticatedAccount actor,
            @PathVariable long id,
            @Valid @RequestBody UnconfirmRequest body) {
        return attendanceService.unconfirm(id, body.reason(), trustedActorId(actor));
    }
}
