package com.hrm.dto;

import com.hrm.enums.PayBasis;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record MyContractDetailResponse(MyContractResponse contract, List<SalaryTerm> salaryTerms) {
    public record SalaryTerm(
            Long salaryTermId, LocalDate effectiveFrom, LocalDate effectiveTo,
            BigDecimal baseSalary, PayBasis payBasis) {}
}
