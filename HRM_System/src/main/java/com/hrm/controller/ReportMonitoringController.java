package com.hrm.controller;

import com.hrm.dto.EmployeeStatisticsResponse;
import com.hrm.dto.PageResponse;
import com.hrm.dto.PayrollStatisticsResponse;
import com.hrm.dto.WorkStatisticsResponse;
import com.hrm.service.PayrollStatisticsService;
import com.hrm.service.ReportDepartmentService;
import java.time.LocalDate;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/reports/monitor")
@PreAuthorize("hasRole('ADMIN') and hasAuthority('REPORT_VIEW')")
public class ReportMonitoringController {
    private final ReportDepartmentService reports;
    public ReportMonitoringController(ReportDepartmentService reports){this.reports=reports;}
    @GetMapping("/departments") public List<ReportDepartmentService.Department> departments(){return reports.departments();}
    @GetMapping("/employees") public EmployeeStatisticsResponse employees(@RequestParam long departmentId){return reports.employees(departmentId);}
    @GetMapping("/employees/details")
    public PageResponse<ReportDepartmentService.EmployeeDetail> employeeDetails(
        @RequestParam String groupBy, @RequestParam(required=false) Long groupId,
        @RequestParam(required=false) String groupCode, @RequestParam(required=false) Long departmentId,
        @RequestParam(defaultValue="0") int page, @RequestParam(defaultValue="10") int size) {
        return reports.employeeDetails(groupBy,groupId,groupCode,departmentId,page,size);
    }
    @GetMapping("/payroll/{batchId}/employees") @PreAuthorize(PayrollStatisticsService.VIEW_AUTHORIZATION)
    public ResponseEntity<PageResponse<ReportDepartmentService.PayrollEmployee>> payrollEmployees(
        @PathVariable long batchId,
        @RequestParam @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate fromDate,
        @RequestParam @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate toDate,
        @RequestParam(required=false) Long departmentId,
        @RequestParam(defaultValue="0") int page, @RequestParam(defaultValue="10") int size) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
            .body(reports.payrollEmployees(fromDate,toDate,batchId,departmentId,page,size));
    }
    @GetMapping("/payroll/{batchId}/employees/{detailId}/lines") @PreAuthorize(PayrollStatisticsService.VIEW_AUTHORIZATION)
    public ResponseEntity<List<ReportDepartmentService.PayrollLine>> payrollLines(
        @PathVariable long batchId, @PathVariable long detailId,
        @RequestParam @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate fromDate,
        @RequestParam @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate toDate,
        @RequestParam(required=false) Long departmentId) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
            .body(reports.payrollLines(fromDate,toDate,batchId,detailId,departmentId));
    }
    @GetMapping("/movements") public List<ReportDepartmentService.Movement> movements(
        @RequestParam @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate fromDate,
        @RequestParam @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate toDate,
        @RequestParam(required=false) Long departmentId){return reports.movements(fromDate,toDate,departmentId);}
    @GetMapping("/contracts") public PageResponse<ReportDepartmentService.ContractDetail> contracts(
        @RequestParam @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate fromDate,
        @RequestParam @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate toDate,
        @RequestParam @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate reportDate,
        @RequestParam(defaultValue="30") int expiringWithinDays,@RequestParam(required=false) Long departmentId,
        @RequestParam String kind,@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="10") int size){
        return reports.contracts(fromDate,toDate,reportDate,expiringWithinDays,departmentId,kind,page,size);}
    @GetMapping("/work") public WorkStatisticsResponse work(
        @RequestParam @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate fromDate,
        @RequestParam @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate toDate,
        @RequestParam long departmentId){return reports.work(fromDate,toDate,departmentId);}
    @GetMapping("/payroll") @PreAuthorize(PayrollStatisticsService.VIEW_AUTHORIZATION)
    public ResponseEntity<PayrollStatisticsResponse> payroll(
        @RequestParam @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate fromDate,
        @RequestParam @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate toDate,
        @RequestParam long departmentId){return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(reports.payroll(fromDate,toDate,departmentId));}
}
