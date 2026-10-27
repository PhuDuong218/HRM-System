package com.hrm.service;

import com.hrm.repository.PayrollRepository;
import com.hrm.repository.projection.EmployeePayrollLineView;
import com.hrm.repository.projection.EmployeePayslipView;
import com.hrm.security.AuthenticatedAccount;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class PayrollService {

    private final PayrollRepository payrollRepository;

    public PayrollService(PayrollRepository payrollRepository) {
        this.payrollRepository = payrollRepository;
    }

    public List<EmployeePayrollLineView> getMySalary(AuthenticatedAccount actor) {
        return payrollRepository.findPublishedSalaryLines(actor.employeeId());
    }

    public List<EmployeePayslipView> getMyPayslips(AuthenticatedAccount actor) {
        return payrollRepository.findPublishedPayslips(actor.employeeId());
    }
}
