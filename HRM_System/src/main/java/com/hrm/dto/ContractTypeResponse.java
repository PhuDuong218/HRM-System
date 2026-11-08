package com.hrm.dto;

public record ContractTypeResponse(
    Long contractTypeId, String typeCode, String typeName, Boolean isActive, com.hrm.enums.ContractTermKind termKind, Integer maxMonths
) {}