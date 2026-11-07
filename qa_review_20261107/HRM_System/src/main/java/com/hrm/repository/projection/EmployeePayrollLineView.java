package com.hrm.repository.projection;

import java.math.BigDecimal;
import java.time.LocalDate;

public interface EmployeePayrollLineView {

    Long getPayrollDetailId();

    String getPeriodCode();

    LocalDate getPeriodStart();

    LocalDate getPeriodEnd();

    String getComponentCode();

    String getComponentName();

    String getDirection();

    BigDecimal getQuantity();

    BigDecimal getUnitRate();

    BigDecimal getMultiplier();

    BigDecimal getAmount();

    String getExplanation();
}
