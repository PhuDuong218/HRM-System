package com.hrm.dto;

import java.time.LocalDate;

public record ExpiringContractResponse(
        Long contractId, Long employeeId, String employeeCode, String employeeName,
        LocalDate endDate, long daysLeft) {}
