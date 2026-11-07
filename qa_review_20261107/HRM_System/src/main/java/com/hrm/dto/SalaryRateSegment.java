package com.hrm.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import com.hrm.enums.PayBasis;

public record SalaryRateSegment(
    Long employeeId, Long contractId, Long salaryTermId,
    LocalDate from, LocalDate to, BigDecimal baseSalary, PayBasis payBasis
) {}