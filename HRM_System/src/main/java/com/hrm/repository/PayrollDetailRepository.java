package com.hrm.repository;

import com.hrm.model.PayrollDetail;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PayrollDetailRepository extends JpaRepository<PayrollDetail, Long> {
    @org.springframework.data.jpa.repository.Query(value = """
            SELECT DISTINCT a.employee_id
            FROM dbo.payroll_batches b
            JOIN dbo.payroll_periods p ON p.period_id = b.period_id
            JOIN dbo.attendance_records a ON a.work_date BETWEEN p.period_start AND p.period_end
            WHERE b.batch_id = :batchId AND b.status = 'NHAP'
              AND a.invalidated_at IS NULL AND a.verification_status = 'DA_XAC_NHAN'
              AND a.attendance_result = 'DI_LAM'
              AND NOT EXISTS (
                  SELECT 1 FROM dbo.contracts c
                  JOIN dbo.contract_salary_terms t ON t.contract_id = c.contract_id
                  WHERE c.employee_id = a.employee_id AND c.status IN ('DA_KY', 'DA_KET_THUC')
                    AND c.start_date <= a.work_date AND (c.end_date IS NULL OR c.end_date >= a.work_date)
                    AND t.effective_from <= a.work_date AND (t.effective_to IS NULL OR t.effective_to >= a.work_date)
                    AND t.base_salary > 0
              )
            ORDER BY a.employee_id
            """, nativeQuery = true)
    List<Long> findEmployeesMissingSalary(@org.springframework.data.repository.query.Param("batchId") Long batchId);

    @org.springframework.data.jpa.repository.Query(value = """
            SELECT DISTINCT a.employee_id
            FROM dbo.payroll_batches b
            JOIN dbo.payroll_periods p ON p.period_id = b.period_id
            JOIN dbo.attendance_records a ON a.work_date BETWEEN p.period_start AND p.period_end
            WHERE b.batch_id = :batchId AND b.status = 'NHAP'
              AND a.invalidated_at IS NULL AND a.verification_status = 'DA_XAC_NHAN'
            ORDER BY a.employee_id
            """, nativeQuery = true)
    List<Long> findDraftEmployeesWithConfirmedAttendance(
            @org.springframework.data.repository.query.Param("batchId") Long batchId);

    @org.springframework.data.jpa.repository.Query(value = """
            SELECT CAST(CASE WHEN EXISTS (
                SELECT 1 FROM dbo.payroll_details d
                JOIN dbo.payroll_snapshots s ON s.payroll_detail_id = d.payroll_detail_id
                WHERE d.batch_id = :batchId AND s.policy_version = 'HRM-DRAFT-2026.1'
            ) THEN 1 ELSE 0 END AS bit)
            """, nativeQuery = true)
    boolean hasProvisionalDraft(@org.springframework.data.repository.query.Param("batchId") Long batchId);

    List<PayrollDetail> findByBatchId(Long batchId);
    Optional<PayrollDetail> findByBatchIdAndEmployeeId(Long batchId, Long employeeId);
    void deleteByBatchId(Long batchId);
}