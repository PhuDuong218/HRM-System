package com.hrm.dto;

public record QualificationLevelDTO(
        Long qualificationLevelId,
        String levelCode,
        String levelName,
        String description,
        Boolean isActive) {
}
