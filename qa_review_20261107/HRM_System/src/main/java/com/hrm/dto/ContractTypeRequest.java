package com.hrm.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record ContractTypeRequest(
    @NotBlank @Size(max = 50)
    @Pattern(regexp = "[A-Za-z0-9_-]+",
             message = "Mã chỉ gồm chữ không dấu, số, _ hoặc -")
    String typeCode,
    @NotBlank @Size(max = 200) String typeName,
    Boolean isActive,
    @jakarta.validation.constraints.NotNull com.hrm.enums.ContractTermKind termKind,
    @jakarta.validation.constraints.Min(1) @jakarta.validation.constraints.Max(36) Integer maxMonths,
    @NotBlank @Size(max=500) String reason
) {}
