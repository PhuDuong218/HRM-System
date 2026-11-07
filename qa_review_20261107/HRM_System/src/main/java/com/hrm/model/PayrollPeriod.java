package com.hrm.model;

import jakarta.persistence.*;
import java.time.LocalDate;

@Entity
@Table(name = "payroll_periods")
public class PayrollPeriod extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "period_id")
    private Long periodId;

    @Column(name = "period_code", unique = true, nullable = false, length = 50)
    private String periodCode;

    @Column(name = "period_start", nullable = false)
    private LocalDate periodStart;

    @Column(name = "period_end", nullable = false)
    private LocalDate periodEnd;

    // --- CONSTRUCTORS ---
    public PayrollPeriod() {
    }

    public PayrollPeriod(String periodCode, LocalDate periodStart, LocalDate periodEnd) {
        this.periodCode = periodCode;
        this.periodStart = periodStart;
        this.periodEnd = periodEnd;
    }

    // --- GETTERS & SETTERS ---
    public Long getPeriodId() {
        return periodId;
    }

    public void setPeriodId(Long periodId) {
        this.periodId = periodId;
    }

    public String getPeriodCode() {
        return periodCode;
    }

    public void setPeriodCode(String periodCode) {
        this.periodCode = periodCode;
    }

    public LocalDate getPeriodStart() {
        return periodStart;
    }

    public void setPeriodStart(LocalDate periodStart) {
        this.periodStart = periodStart;
    }

    public LocalDate getPeriodEnd() {
        return periodEnd;
    }

    public void setPeriodEnd(LocalDate periodEnd) {
        this.periodEnd = periodEnd;
    }
}