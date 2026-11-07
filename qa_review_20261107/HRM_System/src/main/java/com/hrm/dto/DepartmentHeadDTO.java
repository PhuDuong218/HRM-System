package com.hrm.dto;

import java.time.LocalDate;

public record DepartmentHeadDTO(Long assignmentId, Long departmentId, Long employeeId,
        String employeeCode, String fullName, LocalDate effectiveFrom, LocalDate effectiveTo) {}
