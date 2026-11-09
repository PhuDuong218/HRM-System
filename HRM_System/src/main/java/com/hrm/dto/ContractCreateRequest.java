package com.hrm.dto;

import java.time.LocalDate;
import jakarta.validation.constraints.*;

public record ContractCreateRequest(
    @NotNull @Positive Long employeeId,
    @NotNull @Positive Long contractTypeId,
    @Size(max = 50) String contractNumber,
    @NotNull @PastOrPresent LocalDate signedDate,
    @NotNull LocalDate startDate,
    LocalDate endDate,
    com.hrm.enums.ProbationGroup probationGroup
) {}