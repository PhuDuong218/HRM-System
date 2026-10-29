package com.hrm.controller;

import com.hrm.dto.DecisionPayrollResult;
import com.hrm.service.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.access.prepost.PreAuthorize;

@RestController
@PreAuthorize("hasAnyRole('ADMIN','HR')")
public class DecisionPayrollController {
    private final DecisionPayrollService service;
    private final DecisionActorProvider actors;

    public DecisionPayrollController(DecisionPayrollService service, DecisionActorProvider actors) {
        this.service = service;
        this.actors = actors;
    }

    @GetMapping("/employees/{employeeId}/decision-payroll-items")
    public DecisionPayrollResult get(@PathVariable Long employeeId, @RequestParam Long periodId) {
        actors.requireManager();
        return service.getForPeriod(employeeId, periodId);
    }
}