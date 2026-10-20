package com.hrm.dto;

public record EmployeeQualificationDTO(
        Long employeeQualificationId,
        Long employeeId,
        Long qualificationLevelId,
        String levelCode,
        String levelName,
        String major,
        String institution,
        Integer graduationYear,
        String certificateNumber,
        Boolean isPrimary) {
}
