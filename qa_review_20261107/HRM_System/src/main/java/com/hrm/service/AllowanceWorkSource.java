package com.hrm.service;
import com.hrm.dto.AllowanceWorkData;
import java.time.LocalDate;
public interface AllowanceWorkSource {
    AllowanceWorkData load(Long employeeId,LocalDate from,LocalDate to);
}