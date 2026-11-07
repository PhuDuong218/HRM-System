package com.hrm.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import com.hrm.enums.PayBasis;

public record SalaryTermResponse(
    Long salaryTermId, Long contractId,
    LocalDate effectiveFrom, LocalDate effectiveTo,
    BigDecimal baseSalary, PayBasis payBasis, String documentReference,
    BigDecimal officialReferenceSalary, PayBasis officialReferencePayBasis, java.util.List<String> warnings
) {}