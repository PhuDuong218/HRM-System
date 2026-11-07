package com.hrm.dto;

import com.hrm.repository.projection.EmployeeStatisticsRow;

import java.util.List;

public record EmployeeStatisticsResponse(
        List<EmployeeStatisticsRow> byDepartment,
        List<EmployeeStatisticsRow> byPosition,
        List<EmployeeStatisticsRow> byStatus,
        List<EmployeeStatisticsRow> byQualification) {
}
