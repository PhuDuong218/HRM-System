package com.hrm.controller;

import com.hrm.service.ReportDepartmentService;
import com.hrm.dto.PageResponse;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class ReportMonitoringControllerTest {
    @Test void payrollJsonUsesTheSamePeriodEndAsTheSource() throws Exception {
        var service = mock(ReportDepartmentService.class);
        var from = LocalDate.of(2026,11,1); var end = LocalDate.of(2026,11,30);
        var zero = java.math.BigDecimal.ZERO;
        var totals = new com.hrm.dto.PayrollStatisticsResponse.Amounts(zero,zero,zero,zero,zero);
        var batch = new com.hrm.dto.PayrollStatisticsResponse.BatchSummary(1L,"P11",from,end,11L,2,1,totals);
        when(service.payroll(from,end,4L)).thenReturn(new com.hrm.dto.PayrollStatisticsResponse(from,end,1,1,totals,List.of(batch)));
        var mvc = MockMvcBuilders.standaloneSetup(new ReportMonitoringController(service)).build();
        mvc.perform(get("/api/reports/monitor/payroll").param("fromDate","2026-11-01").param("toDate","2026-11-30").param("departmentId","4"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.batches[0].periodEnd").value("2026-11-30"))
            .andExpect(jsonPath("$.batches[0].periodEndExclusive").doesNotExist());
        verify(service).payroll(from,end,4L);
    }

    @Test void mapsEmployeeAndPayrollDrilldownsWithoutChangingTheBatch() throws Exception {
        var service = mock(ReportDepartmentService.class);
        var from = LocalDate.of(2026,11,1); var to = LocalDate.of(2026,11,30);
        when(service.employeeDetails("status",null,"DANG_LAM",4L,1,10)).thenReturn(new PageResponse<>(List.of(),1,10,11,2));
        when(service.payrollEmployees(from,to,11L,4L,0,10)).thenReturn(new PageResponse<>(List.of(),0,10,0,0));
        when(service.payrollLines(from,to,11L,111L,4L)).thenReturn(List.of(new ReportDepartmentService.PayrollLine(2,"LUONG","Salary","ADD",new java.math.BigDecimal("9007199254740993.1234"),null)));
        var mvc = MockMvcBuilders.standaloneSetup(new ReportMonitoringController(service)).build();
        mvc.perform(get("/api/reports/monitor/employees/details").param("groupBy","status").param("groupCode","DANG_LAM").param("departmentId","4").param("page","1"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.totalPages").value(2));
        mvc.perform(get("/api/reports/monitor/payroll/11/employees").param("fromDate","2026-11-01").param("toDate","2026-11-30").param("departmentId","4"))
            .andExpect(status().isOk()).andExpect(header().string("Cache-Control","no-store"));
        mvc.perform(get("/api/reports/monitor/payroll/11/employees/111/lines").param("fromDate","2026-11-01").param("toDate","2026-11-30").param("departmentId","4"))
            .andExpect(status().isOk()).andExpect(header().string("Cache-Control","no-store"))
            .andExpect(jsonPath("$[0].componentCode").value("LUONG"));
        verify(service).payrollLines(from,to,11L,111L,4L);
        mvc.perform(get("/api/reports/monitor/payroll/11/employees").param("fromDate","invalid").param("toDate","2026-11-30"))
            .andExpect(status().isBadRequest());
    }

    @Test void mapsFiltersAndPaginatesContractDetails() throws Exception {
        var service=mock(ReportDepartmentService.class);
        var from=LocalDate.of(2026,9,1);var to=LocalDate.of(2026,9,30);var date=LocalDate.of(2026,9,30);
        when(service.contracts(from,to,date,30,4L,"expiring",1,10)).thenReturn(new PageResponse<>(List.of(),1,10,11,2));
        when(service.movements(from,to,4L)).thenReturn(List.of(new ReportDepartmentService.Movement("2026-09",2,1)));
        var mvc=MockMvcBuilders.standaloneSetup(new ReportMonitoringController(service)).build();
        mvc.perform(get("/api/reports/monitor/contracts").param("fromDate","2026-09-01").param("toDate","2026-09-30")
            .param("reportDate","2026-09-30").param("departmentId","4").param("kind","expiring").param("page","1"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(11)).andExpect(jsonPath("$.totalPages").value(2));
        mvc.perform(get("/api/reports/monitor/movements").param("fromDate","2026-09-01").param("toDate","2026-09-30").param("departmentId","4"))
            .andExpect(status().isOk()).andExpect(jsonPath("$[0].hired").value(2)).andExpect(jsonPath("$[0].terminated").value(1));
        verify(service).contracts(from,to,date,30,4L,"expiring",1,10);
    }
}
