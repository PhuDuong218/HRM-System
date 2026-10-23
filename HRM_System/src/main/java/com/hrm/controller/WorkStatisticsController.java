package com.hrm.controller;

import com.hrm.dto.WorkStatisticsResponse;
import com.hrm.service.WorkStatisticsService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/reports")
public class WorkStatisticsController {

    private final WorkStatisticsService statisticsService;

    public WorkStatisticsController(WorkStatisticsService statisticsService) {
        this.statisticsService = statisticsService;
    }

    @GetMapping("/work")
    @PreAuthorize("hasAuthority('work.report.view')")
    public WorkStatisticsResponse getWorkStatistics(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate) {
        return statisticsService.summarize(fromDate, toDate);
    }
}
