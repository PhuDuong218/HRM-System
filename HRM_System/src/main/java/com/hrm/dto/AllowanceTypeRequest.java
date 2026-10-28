package com.hrm.dto;

import jakarta.validation.constraints.*;

public record AllowanceTypeRequest(
    @NotBlank @Size(max=50) @Pattern(regexp="[A-Za-z0-9_-]+") String typeCode,
    @NotBlank @Size(max=200) String typeName,
    @Size(max=1000) String description,
    Boolean isActive,
    @NotBlank @Size(max=500) String reason
) {}