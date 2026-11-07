
package com.hrm.dto;

import java.time.LocalDate;

public record EmployeeAssignmentDTO(
        Long assignmentId,
        Long employeeId,
        String employeeCode,
        String employeeName,
        Long departmentId,
        String departmentName,
        Long positionId,
        String positionName,
        LocalDate effectiveFrom,
        LocalDate effectiveTo
) {
}
