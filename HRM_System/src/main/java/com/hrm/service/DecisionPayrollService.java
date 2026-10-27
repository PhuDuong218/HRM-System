package com.hrm.service;

import java.math.BigDecimal;
import java.sql.Date;
import java.time.temporal.ChronoUnit;
import java.util.*;
import com.hrm.dto.*;
import com.hrm.enums.*;
import com.hrm.exception.BusinessException;
import com.hrm.repository.*;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DecisionPayrollService {
    private final EmployeeRepository employees;
    private final EmployeeDecisionRepository decisions;
    private final AllowancePeriodReader periods;
    private final DecisionPayrollPolicy policy;
    private final JdbcTemplate jdbc;

    public DecisionPayrollService(EmployeeRepository employees, EmployeeDecisionRepository decisions,
            AllowancePeriodReader periods, DecisionPayrollPolicy policy, JdbcTemplate jdbc) {
        this.employees = employees;
        this.decisions = decisions;
        this.periods = periods;
        this.policy = policy;
        this.jdbc = jdbc;
    }

    @Transactional
    public DecisionPayrollResult getForPeriod(Long employeeId, Long periodId) {
        employees.findByIdForUpdate(employeeId)
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "Không có nhân viên"));
        var period = periods.get(periodId);
        if (period.to().isBefore(period.from()) || ChronoUnit.DAYS.between(period.from(), period.to()) > 365)
            throw conflict("Kỳ phải từ 1 đến 366 ngày");
        Long overlap = jdbc.queryForObject("""
                select count_big(*) from dbo.payroll_periods
                where period_id<>? and period_start<=? and period_end>=?
                """, Long.class, periodId, Date.valueOf(period.to()), Date.valueOf(period.from()));
        if (overlap == null || overlap > 0)
            throw conflict("Kỳ lương giao nhau; chưa thể phân bổ quyết định duy nhất");
        var rows = decisions.findPayrollCandidates(employeeId, DecisionApprovalStatus.DA_DUYET,
                DecisionPayrollEffect.NONE, period.from(), period.to());
        if (!rows.isEmpty())
            policy.requireConfirmed();
        BigDecimal add = BigDecimal.ZERO, deduct = BigDecimal.ZERO;
        List<DecisionPayrollLine> lines = new ArrayList<>();
        Set<Long> ids = new HashSet<>();
        for (var d : rows) {
            if (d.getApprovalStatus() != DecisionApprovalStatus.DA_DUYET ||
                    !employeeId.equals(d.getEmployee().getEmployeeId()) ||
                    d.getPayrollEffect() == null || d.getPayrollEffect() == DecisionPayrollEffect.NONE ||
                    d.getAmount() == null || d.getAmount().signum() <= 0 || d.getApplyDate() == null ||
                    d.getApplyDate().isBefore(period.from()) || d.getApplyDate().isAfter(period.to()) ||
                    !ids.add(d.getDecisionId()))
                throw conflict("Nguồn quyết định sai phạm vi hoặc bị lặp");
            if (d.getPayrollEffect() == DecisionPayrollEffect.ADD)
                add = add.add(d.getAmount());
            else
                deduct = deduct.add(d.getAmount());
            lines.add(new DecisionPayrollLine("employee_decisions/" + d.getDecisionId(), d.getDecisionId(),
                    d.getDecisionNumber(), d.getDecisionType(), d.getApplyDate(), d.getPayrollEffect(), d.getAmount()));
        }
        return new DecisionPayrollResult(employeeId, periodId, period.from(), period.to(), List.copyOf(lines),
                add, deduct, add.subtract(deduct), policy.version());
    }

    private BusinessException conflict(String m) {
        return new BusinessException(HttpStatus.CONFLICT, m);
    }
}