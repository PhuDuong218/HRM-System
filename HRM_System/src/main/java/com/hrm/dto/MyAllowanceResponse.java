package com.hrm.dto;

import com.hrm.enums.AllowanceAmountType;
import com.hrm.enums.AllowanceCalculationBasis;
import java.math.BigDecimal;
import java.time.LocalDate;

public record MyAllowanceResponse(
        Long employeeAllowanceId, Long allowanceTypeId, String typeCode, String typeName,
        LocalDate effectiveFrom, LocalDate effectiveTo, AllowanceCalculationBasis calculationBasis,
        AllowanceAmountType amountType, BigDecimal amount) {}
