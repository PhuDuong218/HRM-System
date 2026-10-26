package com.hrm.controller;

import com.hrm.dto.AllowanceCalculationResult;
import com.hrm.service.AllowanceCalculationService;
import org.springframework.web.bind.annotation.*;

@RestController
public class AllowanceCalculationController {
    private final AllowanceCalculationService service;
    public AllowanceCalculationController(AllowanceCalculationService service) { this.service=service; }
    @GetMapping("/employees/{employeeId}/allowance-calculations")
    public AllowanceCalculationResult calculate(@PathVariable Long employeeId,@RequestParam Long periodId) {
        return service.calculate(employeeId,periodId);
    }
}