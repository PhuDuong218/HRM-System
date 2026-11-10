package com.hrm.controller;

import com.hrm.dto.ReportExportFile;
import com.hrm.service.PayrollStatisticsService;
import com.hrm.service.ReportExportService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/reports")
public class ReportExportController {

    private static final MediaType XLSX = MediaType.parseMediaType(
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");

    private final ReportExportService exports;

    public ReportExportController(ReportExportService exports) {
        this.exports = exports;
    }

    @GetMapping("/employees/export")
    @PreAuthorize(ReportExportService.VIEW_AUTHORIZATION)
    public ResponseEntity<byte[]> exportEmployees() {
        return download(exports.exportEmployees());
    }

    @GetMapping(value="/monitor/employees/export")
    @PreAuthorize(ReportExportService.VIEW_AUTHORIZATION)
    public ResponseEntity<byte[]> exportEmployeeMonitoring(
            @RequestParam @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate toDate,
            @RequestParam(required=false) Long departmentId) {
        return download(exports.exportEmployeeMonitoring(fromDate,toDate,departmentId));
    }

    @GetMapping("/contracts/export")
    @PreAuthorize(ReportExportService.VIEW_AUTHORIZATION)
    public ResponseEntity<byte[]> exportContracts(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate reportDate,
            @RequestParam(defaultValue = "30") int expiringWithinDays,
            @RequestParam(required = false) Long departmentId) {
        return download(exports.exportContracts(fromDate, toDate, reportDate, expiringWithinDays, departmentId));
    }

    @GetMapping("/work/export")
    @PreAuthorize(ReportExportService.VIEW_AUTHORIZATION)
    public ResponseEntity<byte[]> exportWork(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate) {
        return download(exports.exportWork(fromDate, toDate));
    }

    @GetMapping("/monitor/work/export")
    @PreAuthorize(ReportExportService.VIEW_AUTHORIZATION)
    public ResponseEntity<byte[]> exportWorkForDepartment(
            @RequestParam @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate toDate,
            @RequestParam long departmentId) {
        return download(exports.exportWorkForDepartment(fromDate,toDate,departmentId));
    }

    @GetMapping("/payroll/export")
    @PreAuthorize(PayrollStatisticsService.VIEW_AUTHORIZATION)
    public ResponseEntity<byte[]> exportPayroll(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate) {
        return download(exports.exportPayroll(fromDate, toDate));
    }

    @GetMapping("/monitor/payroll/export")
    @PreAuthorize(PayrollStatisticsService.VIEW_AUTHORIZATION)
    public ResponseEntity<byte[]> exportPayrollForDepartment(
            @RequestParam @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate toDate,
            @RequestParam long departmentId) {
        return download(exports.exportPayrollForDepartment(fromDate,toDate,departmentId));
    }

    private ResponseEntity<byte[]> download(ReportExportFile file) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(XLSX);
        headers.setContentDisposition(ContentDisposition.attachment().filename(file.fileName()).build());
        headers.setCacheControl(CacheControl.noStore());
        headers.setContentLength(file.content().length);
        headers.set("X-Content-Type-Options", "nosniff");
        return ResponseEntity.ok().headers(headers).body(file.content());
    }
}
