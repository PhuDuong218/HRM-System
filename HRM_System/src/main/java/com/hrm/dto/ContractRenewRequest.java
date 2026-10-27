package com.hrm.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

public record ContractRenewRequest(
    @NotNull @Valid ContractCreateRequest newContract,
    @NotBlank @Size(max = 500) String reason
) {}