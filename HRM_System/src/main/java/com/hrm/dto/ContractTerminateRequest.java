package com.hrm.dto;

import java.time.LocalDate;
import jakarta.validation.constraints.*;

public record ContractTerminateRequest(
    @NotNull LocalDate terminationDate,
    @NotBlank @Size(max = 500) String reason
) {}