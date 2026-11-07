package com.hrm.repository.projection;

import java.time.LocalDate;
import java.time.LocalDateTime;

public interface EmployeePayslipView {

    Long getPayslipId();

    Long getPayrollDetailId();

    String getPayslipNumber();

    LocalDateTime getIssuedAt();

    String getFileReference();

    String getPeriodCode();

    LocalDate getPeriodStart();

    LocalDate getPeriodEnd();
}
