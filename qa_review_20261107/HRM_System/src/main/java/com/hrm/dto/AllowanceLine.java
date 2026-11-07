package com.hrm.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import com.hrm.enums.AllowanceCalculationBasis;
import com.hrm.enums.AllowanceAmountType;

public record AllowanceLine(Long employeeAllowanceId, Long allowanceTypeId, String typeCode,
        LocalDate from, LocalDate to, AllowanceCalculationBasis basis, BigDecimal unitAmount,
        BigDecimal eligibleUnits, BigDecimal rawAmount, String sourceReference, String policyVersion,
        boolean isTaxable, boolean countsForSocialInsurance, int effectiveDays, int periodDays,
        AllowanceAmountType amountType, BigDecimal configuredAmount, SalaryRateSegment salaryReference) {
}
