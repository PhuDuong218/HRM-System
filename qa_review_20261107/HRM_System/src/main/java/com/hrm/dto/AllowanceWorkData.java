package com.hrm.dto;
import java.time.LocalDate;
import java.util.List;
public record AllowanceWorkData(Long employeeId,LocalDate from,LocalDate to,
        List<EligibleAllowanceDay> days,String sourceReference,String policyVersion) {}