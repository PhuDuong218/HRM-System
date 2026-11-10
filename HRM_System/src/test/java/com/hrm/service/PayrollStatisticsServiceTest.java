package com.hrm.service;

import com.hrm.repository.PayrollStatisticsRepository;
import com.hrm.repository.projection.PayrollStatisticsRow;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.repository.Query;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.security.core.context.SecurityContextHolder;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PayrollStatisticsServiceTest {
    @BeforeEach @AfterEach void clearSecurity() { SecurityContextHolder.clearContext(); }

    @Test void passesTheLastSelectedDateUnchangedAndReturnsTheStoredPeriodEnd() {
        var repository = mock(PayrollStatisticsRepository.class);
        var row = mock(PayrollStatisticsRow.class);
        var from = LocalDate.of(2026,11,1); var to = LocalDate.of(2026,11,30);
        when(row.getPeriodId()).thenReturn(1L);
        when(row.getPeriodCode()).thenReturn("P11");
        when(row.getPeriodStart()).thenReturn(from);
        when(row.getPeriodEnd()).thenReturn(to);
        when(row.getBatchId()).thenReturn(11L);
        when(row.getVersionNo()).thenReturn(2);
        when(row.getEmployeeCount()).thenReturn(1L);
        when(row.getGrossPayroll()).thenReturn(new BigDecimal("1000.0000"));
        when(row.getAllowances()).thenReturn(new BigDecimal("100.0000"));
        when(row.getBonuses()).thenReturn(BigDecimal.ZERO);
        when(row.getDeductions()).thenReturn(new BigDecimal("250.0000"));
        when(repository.summarizeFinalizedBatches(from,to)).thenReturn(List.of(row));
        var result = new PayrollStatisticsService(repository).summarize(from,to);
        verify(repository).summarizeFinalizedBatches(from,to);
        assertEquals(to,result.batches().getFirst().periodEnd());
        assertEquals(0,new BigDecimal("750").compareTo(result.totals().netPayroll()));
    }

    @Test void acceptsTheLastSqlDateWithoutAddingADay() {
        var repository = mock(PayrollStatisticsRepository.class);
        var from = LocalDate.of(9999,12,1); var to = LocalDate.of(9999,12,31);
        when(repository.summarizeFinalizedBatches(from,to)).thenReturn(List.of());
        assertEquals(0,new PayrollStatisticsService(repository).summarize(from,to).batchCount());
        verify(repository).summarizeFinalizedBatches(from,to);
    }

    @Test void nativeReportQueryIncludesTheLastDayButExcludesNextDayAndPartialPeriods() throws Exception {
        var db = new JdbcTemplate(new DriverManagerDataSource("jdbc:h2:mem:report_payroll_period_bounds;MODE=MSSQLServer;DB_CLOSE_DELAY=-1", "sa", ""));
        db.execute("CREATE TABLE payroll_periods(period_id bigint,period_code varchar(30),period_start date,period_end date)");
        db.execute("CREATE TABLE payroll_batches(batch_id bigint,period_id bigint,version_no int,status varchar(20),locked_by bigint,locked_at timestamp)");
        db.execute("CREATE TABLE payroll_details(payroll_detail_id bigint,batch_id bigint,employee_id bigint)");
        db.execute("CREATE TABLE payroll_lines(payroll_detail_id bigint,direction_snapshot varchar(10),component_code_snapshot varchar(30),amount decimal(19,4))");
        db.update("INSERT INTO payroll_periods VALUES(1,'P11','2026-11-01','2026-11-30'),(2,'NEXT','2026-11-01','2026-12-01'),(3,'EARLY','2026-10-31','2026-11-30')");
        db.update("INSERT INTO payroll_batches VALUES(10,1,1,'DA_CHOT',1,CURRENT_TIMESTAMP),(11,1,2,'DA_CHOT',1,CURRENT_TIMESTAMP),(12,1,3,'NHAP',NULL,NULL),(13,1,4,'DA_CHOT',NULL,NULL),(20,2,1,'DA_CHOT',1,CURRENT_TIMESTAMP),(30,3,1,'DA_CHOT',1,CURRENT_TIMESTAMP)");
        db.update("INSERT INTO payroll_details VALUES(101,10,1),(111,11,1),(121,12,1),(131,13,1),(201,20,1),(301,30,1)");
        db.update("INSERT INTO payroll_lines VALUES(101,'ADD','LUONG',999),(111,'ADD','LUONG',1000),(111,'DEDUCT','TRU',250),(121,'ADD','LUONG',888),(131,'ADD','LUONG',777),(201,'ADD','LUONG',2000),(301,'ADD','LUONG',3000)");
        String sql = PayrollStatisticsRepository.class.getMethod("summarizeFinalizedBatches",LocalDate.class,LocalDate.class).getAnnotation(Query.class).value();
        var jdbc = new NamedParameterJdbcTemplate(db);
        var params = new MapSqlParameterSource("fromDate",LocalDate.of(2026,11,1)).addValue("toDate",LocalDate.of(2026,11,30));
        var batches = jdbc.query(sql,params,(r,n)->r.getLong("batchId"));
        assertEquals(List.of(11L),batches);
        params.addValue("toDate",LocalDate.of(2026,11,29));
        assertTrue(jdbc.queryForList(sql,params).isEmpty());
        params.addValue("toDate",LocalDate.of(2026,12,1));
        assertEquals(List.of(11L,20L),jdbc.query(sql,params,(r,n)->r.getLong("batchId")));
    }
}
