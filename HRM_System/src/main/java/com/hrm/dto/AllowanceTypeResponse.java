package com.hrm.dto;

public record AllowanceTypeResponse(Long allowanceTypeId,String typeCode,
        String typeName,String description,Boolean isActive) {}