package com.hrm.dto;
import java.time.LocalDate;
import java.math.BigDecimal;
public record EligibleAllowanceDay(LocalDate date,BigDecimal eligibleDays,BigDecimal eligibleHours) {}