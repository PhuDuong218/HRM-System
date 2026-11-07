package com.hrm.service;

import com.hrm.repository.ContractStatisticsRepository;
import com.hrm.repository.EmployeeStatisticsRepository;
import com.hrm.repository.WorkStatisticsRepository;
import java.io.FileInputStream;
import java.sql.DriverManager;
import java.time.LocalDate;
import java.util.Map;
import java.util.Properties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.data.jpa.repository.Query;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import static org.junit.jupiter.api.Assertions.*;

/** Opt-in diagnostic: executes only the aggregate SELECT queries against local SQL Server. */
@EnabledIfSystemProperty(named = "hrm.reports.readonly", matches = "true")
class ReportReadOnlyDiagnosticTest {
    @Test
    void aggregateQueriesRunOnLocalSchema() throws Exception {
        var properties = new Properties();
        try (var input = new FileInputStream("src/main/resources/application-local.properties")) {
            properties.load(input);
        }
        try (var connection = DriverManager.getConnection(properties.getProperty("spring.datasource.url"),
                properties.getProperty("spring.datasource.username"), properties.getProperty("spring.datasource.password"))) {
            connection.setReadOnly(true);
            var db = new NamedParameterJdbcTemplate(new SingleConnectionDataSource(connection, true));
            var today = LocalDate.now();
            var values = Map.of("asOfDate", today, "fromDate", today.withDayOfMonth(1), "toDate", today,
                    "reportDate", today, "expiringWithinDays", 30, "departmentId", 1L,
                    "startInclusive", today.withDayOfMonth(1).atStartOfDay(), "endExclusive", today.plusDays(1).atStartOfDay());
            for (var name : new String[] {"countByDepartment", "countByPosition", "countByEmploymentStatus", "countByQualification"})
                run(db, EmployeeStatisticsRepository.class, name, values);
            run(db, ContractStatisticsRepository.class, "summarize", values);
            var allDepartments = new java.util.HashMap<String, Object>(values);
            allDepartments.put("departmentId", new org.springframework.jdbc.core.SqlParameterValue(java.sql.Types.BIGINT, null));
            run(db, ContractStatisticsRepository.class, "summarize", allDepartments);
            for (var name : new String[] {"summarizeAttendance", "findApprovedLeaveIntervals", "findConfirmedOvertimeIntervals"})
                run(db, WorkStatisticsRepository.class, name, values);
            if (db.queryForObject("SELECT COL_LENGTH('dbo.attendance_records', 'invalidated_at')", Map.of(), Integer.class) != null)
                run(db, WorkStatisticsRepository.class, "summarizeValidAttendance", values);
        }
    }

    private void run(NamedParameterJdbcTemplate db, Class<?> type, String name, Map<String, ?> values) {
        var method = java.util.Arrays.stream(type.getDeclaredMethods()).filter(m -> m.getName().equals(name) && m.isAnnotationPresent(Query.class)).findFirst().orElseThrow();
        String sql = method.getAnnotation(Query.class).value();
        assertTrue(sql.stripLeading().toLowerCase().startsWith("select") || sql.stripLeading().toLowerCase().startsWith("with"));
        assertNotNull(db.queryForList(sql, values), name);
    }
}
