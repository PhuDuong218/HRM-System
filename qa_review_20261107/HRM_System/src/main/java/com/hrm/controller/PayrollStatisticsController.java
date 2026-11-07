package com.hrm.controller;

import com.hrm.dto.PayrollStatisticsResponse;
import com.hrm.service.PayrollStatisticsService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/reports")
public class PayrollStatisticsController {

    private final PayrollStatisticsService statisticsService;

    public PayrollStatisticsController(PayrollStatisticsService statisticsService) {
        this.statisticsService = statisticsService;
    }

    @GetMapping("/payroll")
    @PreAuthorize(PayrollStatisticsService.VIEW_AUTHORIZATION)
    public ResponseEntity<PayrollStatisticsResponse> getPayrollStatistics(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(statisticsService.summarize(fromDate, toDate));
    }
}
