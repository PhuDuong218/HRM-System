package com.hrm.service;

import com.hrm.dto.AllowancePeriod;
import com.hrm.exception.BusinessException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class AllowancePeriodReader {
    private final JdbcTemplate jdbc;

    public AllowancePeriodReader(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public AllowancePeriod get(Long id) {
        if (id == null || id <= 0)
            throw new BusinessException(HttpStatus.BAD_REQUEST, "periodId phải dương");
        var rows = jdbc.query("select period_id,period_start,period_end from dbo.payroll_periods where period_id=?",
                (rs, n) -> new AllowancePeriod(rs.getLong("period_id"), rs.getDate("period_start").toLocalDate(),
                        rs.getDate("period_end").toLocalDate()),
                id);
        if (rows.isEmpty())
            throw new BusinessException(HttpStatus.NOT_FOUND, "Không có kỳ lương");
        return rows.getFirst();
    }
}