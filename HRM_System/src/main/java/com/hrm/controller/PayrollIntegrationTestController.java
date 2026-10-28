package com.hrm.controller;

import com.hrm.dto.AttendanceSummaryDTO;
import com.hrm.dto.EmployeeSalaryProfileDTO;
import com.hrm.service.provider.AttendanceDataProvider;
import com.hrm.service.provider.SalaryContractProvider;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/payroll-integration-test")
public class PayrollIntegrationTestController {

    private final SalaryContractProvider salaryProvider;
    private final AttendanceDataProvider attendanceProvider;

    public PayrollIntegrationTestController(SalaryContractProvider salaryProvider, AttendanceDataProvider attendanceProvider) {
        this.salaryProvider = salaryProvider;
        this.attendanceProvider = attendanceProvider;
    }

    // Test cổng nhận lương & phụ cấp (P-06)
    @GetMapping("/salary-profile/{employeeId}")
    public ResponseEntity<EmployeeSalaryProfileDTO> getSalaryProfile(@PathVariable Long employeeId) {
        LocalDate start = LocalDate.of(2026, 10, 1);
        LocalDate end = LocalDate.of(2026, 10, 31);
        return ResponseEntity.ok(salaryProvider.getSalaryProfile(employeeId, start, end));
    }

    // Test cổng nhận công, OT và đơn nghỉ (P-07)
    @GetMapping("/attendance/{employeeId}")
    public ResponseEntity<AttendanceSummaryDTO> getAttendance(@PathVariable Long employeeId) {
        LocalDate start = LocalDate.of(2026, 10, 1);
        LocalDate end = LocalDate.of(2026, 10, 31);
        return ResponseEntity.ok(attendanceProvider.getAttendanceSummary(employeeId, start, end));
    }
}