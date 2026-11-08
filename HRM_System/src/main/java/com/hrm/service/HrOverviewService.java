package com.hrm.service;

import com.hrm.dto.ContractStatisticsResponse;
import com.hrm.dto.EmployeeStatisticsResponse;
import com.hrm.dto.WorkStatisticsResponse;
import com.hrm.security.AuthenticatedAccount;
import java.time.LocalDate;
import java.time.ZoneId;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/** Reuses the HR-scoped aggregate reports shown on the reports page. */
@Service
public class HrOverviewService {
    public record Overview(EmployeeStatisticsResponse employees, ContractStatisticsResponse contracts,
                           WorkStatisticsResponse work) {}

    private final EmployeeStatisticsService employees;
    private final ContractStatisticsService contracts;
    private final WorkStatisticsService work;

    public HrOverviewService(EmployeeStatisticsService employees, ContractStatisticsService contracts,
                             WorkStatisticsService work) {
        this.employees = employees;
        this.contracts = contracts;
        this.work = work;
    }

    @Transactional(readOnly = true)
    public Overview get(AuthenticatedAccount actor) {
        if (actor == null || actor.accountId() == null || !"HR".equals(actor.roleCode()))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        LocalDate today = LocalDate.now(ZoneId.of("Asia/Ho_Chi_Minh"));
        LocalDate fromDate = today.withDayOfMonth(1);
        return new Overview(employees.getEmployeeStatistics(),
                contracts.summarize(fromDate, today, today, 30, null),
                work.summarize(fromDate, today));
    }
}
