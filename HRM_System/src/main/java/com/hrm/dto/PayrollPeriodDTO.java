package com.hrm.dto;

import java.time.LocalDate;

public class PayrollPeriodDTO {
    private Long periodId;
    private String periodCode;
    private LocalDate periodStart;
    private LocalDate periodEnd;

    public PayrollPeriodDTO() {}

    public PayrollPeriodDTO(Long periodId, String periodCode, LocalDate periodStart, LocalDate periodEnd) {
        this.periodId = periodId;
        this.periodCode = periodCode;
        this.periodStart = periodStart;
        this.periodEnd = periodEnd;
    }

    public Long getPeriodId() { return periodId; }
    public void setPeriodId(Long periodId) { this.periodId = periodId; }

    public String getPeriodCode() { return periodCode; }
    public void setPeriodCode(String periodCode) { this.periodCode = periodCode; }

    public LocalDate getPeriodStart() { return periodStart; }
    public void setPeriodStart(LocalDate periodStart) { this.periodStart = periodStart; }

    public LocalDate getPeriodEnd() { return periodEnd; }
    public void setPeriodEnd(LocalDate periodEnd) { this.periodEnd = periodEnd; }
}