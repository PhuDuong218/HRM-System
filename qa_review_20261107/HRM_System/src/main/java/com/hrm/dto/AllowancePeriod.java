package com.hrm.dto;
import java.time.LocalDate;
public record AllowancePeriod(Long periodId,LocalDate from,LocalDate to) {}