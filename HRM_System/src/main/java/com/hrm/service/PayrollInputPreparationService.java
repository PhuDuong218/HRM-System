package com.hrm.service;

import com.hrm.dto.AllowanceCalculationResult;
import com.hrm.dto.AllowancePeriod;
import com.hrm.dto.DecisionPayrollResult;
import com.hrm.dto.SalaryRateSegment;
import com.hrm.enums.EmploymentStatus;
import com.hrm.exception.BusinessException;
import com.hrm.repository.EmployeeRepository;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/** Prepares source data for payroll without applying payroll formulas or persisting a batch. */
@Service
public class PayrollInputPreparationService {
    public enum Stage { EMPLOYMENT, SALARY, ALLOWANCE, DECISION }

    public record EmployeeInput(Long employeeId, String employeeCode, String fullName,
            LocalDate employedFrom, LocalDate employedTo, List<SalaryRateSegment> salarySegments,
            AllowanceCalculationResult allowances, DecisionPayrollResult decisions) {}

    public record EmployeeIssue(Long employeeId, String employeeCode, Stage stage, int status, String message) {}

    public record Preparation(AllowancePeriod period, List<EmployeeInput> employees, List<EmployeeIssue> issues) {}

    private final EmployeeRepository employees;
    private final AllowancePeriodReader periods;
    private final SalaryRateService salaryRates;
    private final AllowanceCalculationService allowances;
    private final DecisionPayrollService decisions;
    private final TransactionTemplate employeeRead;

    public PayrollInputPreparationService(EmployeeRepository employees, AllowancePeriodReader periods,
            SalaryRateService salaryRates, AllowanceCalculationService allowances, DecisionPayrollService decisions,
            @Qualifier("transactionManager") PlatformTransactionManager transactionManager) {
        this.employees = employees;
        this.periods = periods;
        this.salaryRates = salaryRates;
        this.allowances = allowances;
        this.decisions = decisions;
        this.employeeRead = new TransactionTemplate(transactionManager);
        this.employeeRead.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.employeeRead.setReadOnly(true);
    }

    public Preparation prepare(LocalDate from, LocalDate to) {
        if (from == null || to == null || to.isBefore(from) || ChronoUnit.DAYS.between(from, to) > 365)
            throw new BusinessException(HttpStatus.BAD_REQUEST, "Kỳ lương phải từ 1 đến 366 ngày");
        var period = periods.getByDates(from, to);
        var ready = new ArrayList<EmployeeInput>();
        var issues = new ArrayList<EmployeeIssue>();
        for (var employee : employees.findEmployedDuring(from, to)) {
            Long employeeId = employee.getEmployeeId();
            try {
                // A business failure can mark the source transaction rollback-only. Keep it local to this employee.
                ready.add(employeeRead.execute(status -> readEmployee(employeeId, period)));
            } catch (SourceFailure failure) {
                issues.add(new EmployeeIssue(employeeId, employee.getEmployeeCode(), failure.stage,
                        failure.cause.getStatus().value(), failure.cause.getMessage()));
            }
        }
        return new Preparation(period, List.copyOf(ready), List.copyOf(issues));
    }

    private EmployeeInput readEmployee(Long employeeId, AllowancePeriod period) {
        var employee = read(Stage.EMPLOYMENT, () -> employees.findByIdForUpdate(employeeId)
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "Không có nhân viên")));
        var hire = employee.getHireDate();
        var end = employee.getTerminationDate();
        if (hire == null || (end == null && employee.getEmploymentStatus() == EmploymentStatus.DA_NGHI)) {
            throw new SourceFailure(Stage.EMPLOYMENT,
                    new BusinessException(HttpStatus.CONFLICT, "Thiếu ngày bắt đầu hoặc ngày nghỉ việc"));
        }
        LocalDate from = hire.isAfter(period.from()) ? hire : period.from();
        LocalDate to = end != null && end.isBefore(period.to()) ? end : period.to();
        if (to.isBefore(from)) {
            throw new SourceFailure(Stage.EMPLOYMENT,
                    new BusinessException(HttpStatus.CONFLICT, "Quan hệ làm việc không còn giao với kỳ"));
        }
        var segments = read(Stage.SALARY, () -> salaryRates.getRatesForPeriod(employeeId, from, to));
        var allowanceResult = read(Stage.ALLOWANCE, () -> allowances.calculate(employeeId, period.periodId()));
        var decisionResult = read(Stage.DECISION, () -> decisions.getForPeriod(employeeId, period.periodId()));
        return new EmployeeInput(employeeId, employee.getEmployeeCode(), employee.getFullName(), from, to,
                segments, allowanceResult, decisionResult);
    }

    private <T> T read(Stage stage, Supplier<T> supplier) {
        try {
            return supplier.get();
        } catch (BusinessException cause) {
            throw new SourceFailure(stage, cause);
        }
    }

    private static class SourceFailure extends RuntimeException {
        private final Stage stage;
        private final BusinessException cause;

        private SourceFailure(Stage stage, BusinessException cause) {
            super(cause);
            this.stage = stage;
            this.cause = cause;
        }
    }
}
