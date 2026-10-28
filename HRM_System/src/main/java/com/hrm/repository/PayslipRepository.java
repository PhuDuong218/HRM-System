package com.hrm.repository;

import com.hrm.model.Payslip;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface PayslipRepository extends JpaRepository<Payslip, Long> {
    Optional<Payslip> findByPayrollDetailId(Long payrollDetailId);
    boolean existsByPayrollDetailId(Long payrollDetailId);
}