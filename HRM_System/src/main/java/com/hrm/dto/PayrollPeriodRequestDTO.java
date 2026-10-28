package com.hrm.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

public class PayrollPeriodRequestDTO {

    @NotBlank(message = "Mã kỳ lương không được để trống!")
    @Size(max = 50, message = "Mã kỳ lương tối đa 50 ký tự!")
    private String periodCode;

    @NotNull(message = "Ngày bắt đầu kỳ không được để trống!")
    private LocalDate periodStart;

    @NotNull(message = "Ngày kết thúc kỳ không được để trống!")
    private LocalDate periodEnd;

    public String getPeriodCode() { return periodCode; }
    public void setPeriodCode(String periodCode) { this.periodCode = periodCode; }

    public LocalDate getPeriodStart() { return periodStart; }
    public void setPeriodStart(LocalDate periodStart) { this.periodStart = periodStart; }

    public LocalDate getPeriodEnd() { return periodEnd; }
    public void setPeriodEnd(LocalDate periodEnd) { this.periodEnd = periodEnd; }
}