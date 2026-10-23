package com.hrm.controller;

import com.hrm.repository.projection.EmployeePayrollLineView;
import com.hrm.repository.projection.EmployeePayslipView;
import com.hrm.security.AuthenticatedAccount;
import com.hrm.service.PayrollService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/me")
public class PayrollController {

    private final PayrollService payrollService;

    public PayrollController(PayrollService payrollService) {
        this.payrollService = payrollService;
    }

    @GetMapping("/salary")
    @PreAuthorize("hasAuthority('payroll.self.view')")
    public List<EmployeePayrollLineView> getMySalary(Authentication authentication) {
        return payrollService.getMySalary((AuthenticatedAccount) authentication.getPrincipal());
    }

    @GetMapping("/payslips")
    @PreAuthorize("hasAuthority('payslip.self.view')")
    public List<EmployeePayslipView> getMyPayslips(Authentication authentication) {
        return payrollService.getMyPayslips((AuthenticatedAccount) authentication.getPrincipal());
    }
}
