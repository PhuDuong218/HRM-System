package com.hrm.service;

import com.hrm.dto.EmployeeStatisticsResponse;
import com.hrm.security.AuthenticatedAccount;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class HrOverviewServiceTest {
    @Test
    void hrOverviewDelegatesToReportServices() {
        var employees = mock(EmployeeStatisticsService.class);
        var contracts = mock(ContractStatisticsService.class);
        var work = mock(WorkStatisticsService.class);
        var report = new EmployeeStatisticsResponse(List.of(), List.of(), List.of(), List.of());
        when(employees.getEmployeeStatistics()).thenReturn(report);
        var service = new HrOverviewService(employees, contracts, work);
        var result = service.get(new AuthenticatedAccount(7L, 9L, "hr", "HR"));
        assertSame(report, result.employees());
        LocalDate today = LocalDate.now(ZoneId.of("Asia/Ho_Chi_Minh"));
        verify(contracts).summarize(today.withDayOfMonth(1), today, today, 30, null);
        verify(work).summarize(today.withDayOfMonth(1), today);
        assertThrows(ResponseStatusException.class,
                () -> service.get(new AuthenticatedAccount(8L, 10L, "employee", "EMPLOYEE")));
    }
}
