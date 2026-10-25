package com.hrm.repository;

import com.hrm.model.PayrollDetail;
import com.hrm.repository.projection.PayrollStatisticsRow;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;

public interface PayrollStatisticsRepository extends Repository<PayrollDetail, Long> {

    @Query(value = """
            select pp.period_id as periodId,
                   pp.period_code as periodCode,
                   pp.period_start as periodStart,
                   pp.period_end as periodEndExclusive,
                   pb.batch_id as batchId,
                   pb.version_no as versionNo,
                   count(distinct pd.employee_id) as employeeCount,
                   sum(case when pl.direction_snapshot = 'ADD'
                            then pl.amount else 0 end) as grossPayroll,
                   sum(case when pl.direction_snapshot = 'ADD' and pl.component_code_snapshot = 'PHU_CAP'
                            then pl.amount else 0 end) as allowances,
                   sum(case when pl.direction_snapshot = 'ADD' and pl.component_code_snapshot = 'THUONG'
                            then pl.amount else 0 end) as bonuses,
                   sum(case when pl.direction_snapshot = 'DEDUCT'
                            then pl.amount else 0 end) as deductions
            from payroll_periods pp
            join payroll_batches pb on pb.period_id = pp.period_id
            join payroll_details pd on pd.batch_id = pb.batch_id
            join payroll_lines pl on pl.payroll_detail_id = pd.payroll_detail_id
            where pp.period_start >= :fromDate
              and pp.period_end <= :endExclusive
              and pb.status = 'DA_CHOT'
              and pb.locked_by is not null
              and pb.locked_at is not null
              and not exists (
                  select 1
                  from payroll_batches newer
                  where newer.period_id = pb.period_id
                    and newer.status = 'DA_CHOT'
                    and newer.locked_by is not null
                    and newer.locked_at is not null
                    and newer.version_no > pb.version_no
              )
            group by pp.period_id, pp.period_code, pp.period_start, pp.period_end,
                     pb.batch_id, pb.version_no
            order by pp.period_start, pp.period_id
            """, nativeQuery = true)
    List<PayrollStatisticsRow> summarizeFinalizedBatches(
            @Param("fromDate") LocalDate fromDate,
            @Param("endExclusive") LocalDate endExclusive);
}
