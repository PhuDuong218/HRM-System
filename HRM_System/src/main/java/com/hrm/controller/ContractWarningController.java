package com.hrm.controller;

import com.hrm.dto.*;
import com.hrm.service.ContractWarningService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/** Dedicated warning routes; intentionally independent of Viet's ContractStatistics module. */
@RestController
@PreAuthorize("hasAnyRole('ADMIN','HR')")
public class ContractWarningController {
    private final ContractWarningService service;

    public ContractWarningController(ContractWarningService service) {
        this.service = service;
    }

    @GetMapping("/contracts/expiring")
    public PageResponse<ExpiringContractResponse> expiring(
            @RequestParam(defaultValue = "30") int days,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        return service.expiring(days, page, size);
    }

    @GetMapping("/employees/without-active-contract")
    public PageResponse<EmployeeWithoutActiveContractResponse> withoutActive(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        return service.withoutActive(page, size);
    }
}
