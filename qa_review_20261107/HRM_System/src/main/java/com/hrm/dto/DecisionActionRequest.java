package com.hrm.dto;
import jakarta.validation.constraints.*;
public record DecisionActionRequest(@NotBlank @Size(max=500) String reason) {}