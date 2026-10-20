package com.hrm.dto;

import java.time.LocalDate;
import jakarta.validation.constraints.*;

public record ContractCreateRequest(
    @NotNull @Positive Long employeeId,
    @NotNull @Positive Long contractTypeId,
    @NotBlank @Size(max = 50)
    @Pattern(regexp = "[A-Za-z0-9/_-]+") String contractNumber,
    @NotNull @PastOrPresent LocalDate signedDate,
    @NotNull LocalDate startDate,
    LocalDate endDate
) {}