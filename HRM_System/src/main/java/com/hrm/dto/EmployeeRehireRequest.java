package com.hrm.dto;
import java.time.LocalDate;
import jakarta.validation.constraints.*;
public record EmployeeRehireRequest(@NotNull @PastOrPresent LocalDate effectiveDate,
 @NotNull @Positive Long departmentId,@NotNull @Positive Long positionId,@NotBlank @Size(max=1000) String reason) {}
