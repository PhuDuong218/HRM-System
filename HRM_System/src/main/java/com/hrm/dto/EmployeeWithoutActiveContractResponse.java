package com.hrm.dto;

import java.time.LocalDate;

public record EmployeeWithoutActiveContractResponse(
        Long employeeId, String employeeCode, String employeeName,
        LocalDate lastContractEndDate, Long overdueDays, boolean overdueOver30,
        LocalDate nextContractStart) {}
