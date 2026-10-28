package com.hrm.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

public record AllowanceChangeRequest(@NotNull @Valid EmployeeAllowanceRequest newAllowance) {}