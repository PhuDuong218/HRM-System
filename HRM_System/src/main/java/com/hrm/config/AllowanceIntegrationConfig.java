package com.hrm.config;

import com.hrm.dto.AllowanceWorkData;
import com.hrm.dto.EligibleAllowanceDay;
import com.hrm.exception.BusinessException;
import com.hrm.service.AllowanceWorkSource;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Configuration
public class AllowanceIntegrationConfig {

    @Bean
    @Primary
    public AllowanceWorkSource databaseAllowanceWorkSource(JdbcTemplate jdbc) {
        return (employeeId, from, to) -> {
            Map<LocalDate, EligibleAllowanceDay> byDate = new HashMap<>();
            jdbc.query("""
                SELECT ws.work_date,
                       COALESCE(ar.check_in_reviewed_value, ar.actual_start) AS eff_start,
                       COALESCE(ar.check_out_reviewed_value, ar.actual_end) AS eff_end,
                       COALESCE(ar.actual_break_minutes, ws.planned_break_minutes) AS break_mins,
                       ar.attendance_result
                FROM dbo.work_schedules ws
                LEFT JOIN dbo.attendance_records ar ON ar.schedule_id = ws.schedule_id AND ar.invalidated_at IS NULL
                WHERE ws.employee_id = ?
                  AND ws.cancelled_at IS NULL
                  AND ws.work_date BETWEEN ? AND ?
            """, rs -> {
                LocalDate d = rs.getDate("work_date").toLocalDate();
                String res = rs.getString("attendance_result");
                Timestamp sTs = rs.getTimestamp("eff_start");
                Timestamp eTs = rs.getTimestamp("eff_end");
                int brk = rs.getInt("break_mins");
                if ("DI_LAM".equals(res) && sTs != null && eTs != null) {
                    LocalDateTime s = sTs.toLocalDateTime();
                    LocalDateTime e = eTs.toLocalDateTime();
                    long mins = Math.max(0, ChronoUnit.MINUTES.between(s, e) - brk);
                    BigDecimal hrs = BigDecimal.valueOf(mins).divide(BigDecimal.valueOf(60), 4, RoundingMode.HALF_UP)
                            .min(new BigDecimal("24"));
                    byDate.put(d, new EligibleAllowanceDay(d, BigDecimal.ONE, hrs));
                }
            }, employeeId, from, to);

            List<EligibleAllowanceDay> allDays = new ArrayList<>();
            for (LocalDate d = from; !d.isAfter(to); d = d.plusDays(1)) {
                allDays.add(byDate.getOrDefault(d, new EligibleAllowanceDay(d, BigDecimal.ZERO, BigDecimal.ZERO)));
            }
            return new AllowanceWorkData(employeeId, from, to, allDays, "DB_ATTENDANCE_RECORDS", "HRM-2026.1");
        };
    }

    @Bean
    @ConditionalOnMissingBean(AllowanceWorkSource.class)
    public AllowanceWorkSource unconfiguredAllowanceWorkSource() {
        return (employeeId, from, to) -> {
            throw new BusinessException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Chưa nối nguồn công đủ điều kiện cho phụ cấp");
        };
    }
}
