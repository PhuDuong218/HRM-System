package com.hrm.repository;

import com.hrm.model.PayrollLine;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PayrollLineRepository extends JpaRepository<PayrollLine, Long> {
    List<PayrollLine> findByPayrollDetailIdOrderByLineNoAsc(Long payrollDetailId);
    void deleteByPayrollDetailIdIn(List<Long> payrollDetailIds);
}