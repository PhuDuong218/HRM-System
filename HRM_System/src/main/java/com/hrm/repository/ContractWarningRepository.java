package com.hrm.repository;

import java.sql.Date;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import com.hrm.dto.EmployeeWithoutActiveContractResponse;
import com.hrm.dto.ExpiringContractResponse;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/** Two SQL statements per page (count + projection), no entity/lazy-loading queries. */
@Repository
public class ContractWarningRepository {
    private static final String EXPIRING_FROM = """
            from dbo.contracts c
            join dbo.employees e on e.employee_id=c.employee_id
            where e.employment_status='DANG_LAM' and c.status='DA_KY'
              and c.end_date>=:today and c.end_date<=:until
            """;
    private static final String WITHOUT_ACTIVE_FROM = """
            from dbo.employees e
            where e.employment_status='DANG_LAM'
              and not exists (
                select 1 from dbo.contracts active_contract
                where active_contract.employee_id=e.employee_id
                  and active_contract.status<>'DA_HUY'
                  and active_contract.start_date<=:today
                  and (active_contract.end_date is null or active_contract.end_date>=:today)
              )
            """;

    private final NamedParameterJdbcTemplate jdbc;

    public ContractWarningRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public long countExpiring(LocalDate today, LocalDate until) {
        return jdbc.queryForObject("select count(*) " + EXPIRING_FROM, dates(today, until), Long.class);
    }

    public List<ExpiringContractResponse> findExpiring(LocalDate today, LocalDate until, long offset, int size) {
        return jdbc.query("""
                select c.contract_id,e.employee_id,e.employee_code,e.full_name,c.end_date
                """ + EXPIRING_FROM + """
                order by c.end_date asc,c.contract_id asc
                offset :offset rows fetch next :size rows only
                """, dates(today, until).addValue("offset", offset).addValue("size", size),
                (rs, row) -> new ExpiringContractResponse(rs.getLong("contract_id"), rs.getLong("employee_id"),
                        rs.getString("employee_code"), rs.getString("full_name"), date(rs, "end_date"),
                        ChronoUnit.DAYS.between(today, date(rs, "end_date"))));
    }

    public long countWithoutActive(LocalDate today) {
        return jdbc.queryForObject("select count(*) " + WITHOUT_ACTIVE_FROM, dates(today, null), Long.class);
    }

    public List<EmployeeWithoutActiveContractResponse> findWithoutActive(LocalDate today, long offset, int size) {
        // Both correlated date aggregates are resolved by SQL in this same page query.
        // Employee/date predicates can use IX_contracts_employee_dates; do not force an index hint.
        return jdbc.query("""
                select e.employee_id,e.employee_code,e.full_name,
                  (select max(past.end_date) from dbo.contracts past
                   where past.employee_id=e.employee_id and past.status<>'DA_HUY'
                     and past.end_date<:today) as last_contract_end_date,
                  (select min(future.start_date) from dbo.contracts future
                   where future.employee_id=e.employee_id and future.status<>'DA_HUY'
                     and future.start_date>:today) as next_contract_start
                """ + WITHOUT_ACTIVE_FROM + """
                order by e.employee_id asc
                offset :offset rows fetch next :size rows only
                """, dates(today, null).addValue("offset", offset).addValue("size", size), (rs, row) -> {
                    LocalDate lastEnd = date(rs, "last_contract_end_date");
                    Long overdue = lastEnd == null ? null : ChronoUnit.DAYS.between(lastEnd, today);
                    return new EmployeeWithoutActiveContractResponse(rs.getLong("employee_id"),
                            rs.getString("employee_code"), rs.getString("full_name"), lastEnd,
                            overdue, overdue != null && overdue > 30, date(rs, "next_contract_start"));
                });
    }

    private MapSqlParameterSource dates(LocalDate today, LocalDate until) {
        return new MapSqlParameterSource("today", Date.valueOf(today))
                .addValue("until", until == null ? null : Date.valueOf(until));
    }

    private LocalDate date(ResultSet rs, String column) throws SQLException {
        Date value = rs.getDate(column);
        return value == null ? null : value.toLocalDate();
    }
}
