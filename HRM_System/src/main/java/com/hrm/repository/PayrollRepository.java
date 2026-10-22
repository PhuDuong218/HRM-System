package com.hrm.repository;

import com.hrm.repository.projection.EmployeePayrollLineView;
import com.hrm.repository.projection.EmployeePayslipView;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface PayrollRepository extends JpaRepository<com.hrm.model.PayrollDetail, Long> {

    @Query(value = """
            select pd.payroll_detail_id as payrollDetailId,
                   pp.period_code as periodCode,
                   pp.period_start as periodStart,
                   pp.period_end as periodEnd,
                   pl.component_code_snapshot as componentCode,
                   pl.component_name_snapshot as componentName,
                   pl.direction_snapshot as direction,
                   pl.quantity as quantity,
                   pl.unit_rate as unitRate,
                   pl.multiplier as multiplier,
                   pl.amount as amount,
                   pl.explanation as explanation
            from payroll_details pd
            join payroll_batches pb on pb.batch_id = pd.batch_id
            join payroll_periods pp on pp.period_id = pb.period_id
            join payroll_lines pl on pl.payroll_detail_id = pd.payroll_detail_id
            where pd.employee_id = :employeeId
              and pb.status = 'PUBLISHED'
            order by pp.period_end desc, pl.line_no
            """, nativeQuery = true)
    List<EmployeePayrollLineView> findPublishedSalaryLines(@Param("employeeId") Long employeeId);

    @Query(value = """
            select ps.payslip_id as payslipId,
                   ps.payroll_detail_id as payrollDetailId,
                   ps.payslip_number as payslipNumber,
                   ps.issued_at as issuedAt,
                   ps.file_reference as fileReference,
                   pp.period_code as periodCode,
                   pp.period_start as periodStart,
                   pp.period_end as periodEnd
            from payslips ps
            join payroll_details pd on pd.payroll_detail_id = ps.payroll_detail_id
            join payroll_batches pb on pb.batch_id = pd.batch_id
            join payroll_periods pp on pp.period_id = pb.period_id
            where pd.employee_id = :employeeId
              and pb.status = 'PUBLISHED'
            order by pp.period_end desc
            """, nativeQuery = true)
    List<EmployeePayslipView> findPublishedPayslips(@Param("employeeId") Long employeeId);
}
