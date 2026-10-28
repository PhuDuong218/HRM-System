package com.hrm.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

public record SalaryTermChangeRequest(@NotNull @Valid SalaryTermRequest newTerm) {}