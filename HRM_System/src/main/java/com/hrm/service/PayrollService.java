package com.hrm.service;

import com.hrm.repository.PayrollRepository;
import com.hrm.repository.projection.EmployeePayrollLineView;
import com.hrm.repository.projection.EmployeePayslipView;
import com.hrm.security.AuthenticatedAccount;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class PayrollService {

    private final PayrollRepository payrollRepository;

    @Autowired(required = false)
    @Lazy
    private PayrollExecutionService payrollExecutionService;

    public PayrollService(PayrollRepository payrollRepository) {
        this.payrollRepository = payrollRepository;
    }

    public List<EmployeePayrollLineView> getMySalary(AuthenticatedAccount actor) {
        if (payrollExecutionService != null) {
            payrollExecutionService.upgradeAllLegacyBatchesIfNeeded();
        }
        return payrollRepository.findPublishedSalaryLines(actor.employeeId());
    }

    public List<EmployeePayslipView> getMyPayslips(AuthenticatedAccount actor) {
        if (payrollExecutionService != null) {
            payrollExecutionService.upgradeAllLegacyBatchesIfNeeded();
        }
        return payrollRepository.findPublishedPayslips(actor.employeeId());
    }
}
