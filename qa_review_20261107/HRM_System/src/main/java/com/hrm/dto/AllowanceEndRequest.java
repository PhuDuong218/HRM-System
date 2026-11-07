package com.hrm.dto;

import java.time.LocalDate;
import jakarta.validation.constraints.*;

public record AllowanceEndRequest(@NotNull LocalDate stopFrom,
        @NotBlank @Size(max=500) String reason) {}