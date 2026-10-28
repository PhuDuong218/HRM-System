
package com.hrm.repository;

import com.hrm.model.Contract;
import com.hrm.repository.projection.ContractStatisticsRow;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.Optional;

public interface ContractStatisticsRepository extends JpaRepository<Contract, Long> {

  @Query(value = """
      select count(c.contract_id) as contractCount,
             sum(case when c.start_date <= :reportDate
                        and (c.end_date is null or c.end_date >= :reportDate)
                        then 1 else 0 end) as effectiveCount,
             sum(case when c.end_date >= :reportDate
                        and c.end_date <= dateadd(day, :expiringWithinDays, :reportDate)
                        then 1 else 0 end) as expiringCount
      from contracts c
      where c.status = 'DA_KY'
        and c.start_date <= :toDate
        and (c.end_date is null or c.end_date >= :fromDate)
        and (:departmentId is null or exists (
            select 1
            from employee_assignments ea
            where ea.employee_id = c.employee_id
              and ea.department_id = :departmentId
              and ea.effective_from <= :toDate
              and (ea.effective_to is null or ea.effective_to > :fromDate)
              and (c.end_date is null or ea.effective_from <= c.end_date)
              and (ea.effective_to is null or ea.effective_to > c.start_date)
        ))
      """, nativeQuery = true)
  Optional<ContractStatisticsRow> summarize(
      @Param("fromDate") LocalDate fromDate,
      @Param("toDate") LocalDate toDate,
      @Param("reportDate") LocalDate reportDate,
      @Param("expiringWithinDays") int expiringWithinDays,
      @Param("departmentId") Long departmentId);
}
