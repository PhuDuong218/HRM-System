package com.hrm.service;

import java.sql.Date;
import java.time.LocalDate;
import com.hrm.exception.BusinessException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PayrollHistoryGuard {
    private final JdbcTemplate jdbc;
    public PayrollHistoryGuard(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Transactional(propagation = Propagation.MANDATORY)
    public void assertMutable(Long employeeId, LocalDate from, LocalDate to) {
        LocalDate upper = to == null ? LocalDate.of(9999, 12, 31) : to;
        if (from.isAfter(upper)) return;
        Long count = jdbc.queryForObject("""
            select count_big(*)
            from dbo.payroll_details d
            join dbo.payroll_batches b on b.batch_id=d.batch_id
            join dbo.payroll_periods p on p.period_id=b.period_id
            where d.employee_id=? and b.status='DA_CHOT'
              and p.period_start<=? and p.period_end>=?
            """, Long.class, employeeId, Date.valueOf(upper), Date.valueOf(from));
        if (count != null && count > 0) {
            throw new BusinessException(HttpStatus.CONFLICT,
                    "Khoảng thay đổi giao kỳ lương đã chốt; dùng quy trình điều chỉnh");
        }
    }
}