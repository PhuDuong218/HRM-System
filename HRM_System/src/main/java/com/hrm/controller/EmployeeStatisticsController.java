package com.hrm.controller;

import com.hrm.dto.EmployeeStatisticsResponse;
import com.hrm.service.EmployeeStatisticsService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/reports")
public class EmployeeStatisticsController {

    private final EmployeeStatisticsService statisticsService;

    public EmployeeStatisticsController(EmployeeStatisticsService statisticsService) {
        this.statisticsService = statisticsService;
    }

    @GetMapping("/employees")
    @PreAuthorize("hasAuthority('employee.report.view')")
    public EmployeeStatisticsResponse getEmployeeStatistics() {
        return statisticsService.getEmployeeStatistics();
    }
}
