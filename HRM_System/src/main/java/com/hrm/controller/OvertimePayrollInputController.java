package com.hrm.controller;

import com.hrm.dto.OvertimeDTOs.PayrollInputs;
import com.hrm.security.AuthenticatedAccount;
import com.hrm.service.OvertimePayrollInputService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/**
 * Read-only OT payroll input per payroll period. Same gate as the other OT
 * endpoints.
 */
@RestController
@RequestMapping("/api/overtime/payroll-inputs")
@ConditionalOnProperty(prefix = "hrm.task", name = "api-enabled", havingValue = "true", matchIfMissing = false)
public class OvertimePayrollInputController {
    private final OvertimePayrollInputService service;

    public OvertimePayrollInputController(OvertimePayrollInputService service) {
        this.service = service;
    }

    @GetMapping
    public PayrollInputs forPeriod(@AuthenticationPrincipal AuthenticatedAccount a,
            @RequestParam long periodId) {
        return service.forPeriod(a, periodId);
    }
}
