package com.hrm.dto;

import java.time.LocalDate;
import com.hrm.enums.TerminationReasonType;
import jakarta.validation.constraints.*;

public record ContractTerminateRequest(
    @NotNull LocalDate terminationDate,
    @NotBlank @Size(max = 500) String reason,
    @NotNull TerminationReasonType reasonType
) {}
