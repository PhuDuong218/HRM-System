package com.hrm.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import com.hrm.enums.AllowanceCalculationBasis;

public record EmployeeAllowanceResponse(Long employeeAllowanceId,Long employeeId,
    Long allowanceTypeId,String typeCode,String typeName,LocalDate effectiveFrom,
    LocalDate effectiveTo,AllowanceCalculationBasis calculationBasis,BigDecimal amount,String conditionNote) {}