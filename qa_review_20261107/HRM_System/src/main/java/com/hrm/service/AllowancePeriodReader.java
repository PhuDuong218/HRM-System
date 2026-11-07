package com.hrm.service;

import com.hrm.dto.AllowancePeriod;
import com.hrm.exception.BusinessException;
import java.time.LocalDate;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;

@Service
public class AllowancePeriodReader {
    private static final RowMapper<AllowancePeriod> PERIOD_ROW =
            (rs, n) -> new AllowancePeriod(rs.getLong("period_id"),
                    rs.getDate("period_start").toLocalDate(), rs.getDate("period_end").toLocalDate());
    private final JdbcTemplate jdbc;

    public AllowancePeriodReader(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public AllowancePeriod get(Long id) {
        if (id == null || id <= 0)
            throw new BusinessException(HttpStatus.BAD_REQUEST, "periodId phải dương");
        var rows = jdbc.query("select period_id,period_start,period_end from dbo.payroll_periods where period_id=?",
                PERIOD_ROW, id);
        if (rows.isEmpty())
            throw new BusinessException(HttpStatus.NOT_FOUND, "Không có kỳ lương");
        return rows.getFirst();
    }

    public AllowancePeriod getByDates(LocalDate from, LocalDate to) {
        if (from == null || to == null || to.isBefore(from))
            throw new BusinessException(HttpStatus.BAD_REQUEST, "Khoảng ngày kỳ lương không hợp lệ");
        var rows = jdbc.query("""
                select period_id,period_start,period_end from dbo.payroll_periods
                where period_start=? and period_end=?
                """, PERIOD_ROW, from, to);
        if (rows.isEmpty())
            throw new BusinessException(HttpStatus.NOT_FOUND, "Không tìm thấy kỳ lương đúng khoảng ngày");
        if (rows.size() > 1)
            throw new BusinessException(HttpStatus.CONFLICT, "Có nhiều kỳ lương cùng ngày bắt đầu và kết thúc");
        return rows.getFirst();
    }
}
