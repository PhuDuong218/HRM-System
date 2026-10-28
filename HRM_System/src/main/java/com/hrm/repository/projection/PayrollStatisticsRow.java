package com.hrm.repository.projection;

import java.math.BigDecimal;
import java.time.LocalDate;

public interface PayrollStatisticsRow {

    Long getPeriodId();

    String getPeriodCode();

    LocalDate getPeriodStart();

    LocalDate getPeriodEndExclusive();

    Long getBatchId();

    Integer getVersionNo();

    Long getEmployeeCount();

    BigDecimal getGrossPayroll();

    BigDecimal getAllowances();

    BigDecimal getBonuses();

    BigDecimal getDeductions();
}
