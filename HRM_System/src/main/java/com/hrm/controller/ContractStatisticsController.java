package com.hrm.controller;

import com.hrm.dto.ContractStatisticsResponse;
import com.hrm.service.ContractStatisticsService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/reports")
public class ContractStatisticsController {

    private final ContractStatisticsService statisticsService;

    public ContractStatisticsController(ContractStatisticsService statisticsService) {
        this.statisticsService = statisticsService;
    }

    @GetMapping("/contracts")
    @PreAuthorize("hasAuthority('contract.report.view')")
    public ContractStatisticsResponse getContractStatistics(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate reportDate,
            @RequestParam(defaultValue = "30") int expiringWithinDays,
            @RequestParam(required = false) Long departmentId) {
        LocalDate evaluatedAt = reportDate == null ? LocalDate.now() : reportDate;
        return statisticsService.summarize(
                fromDate, toDate, evaluatedAt, expiringWithinDays, departmentId);
    }
}
