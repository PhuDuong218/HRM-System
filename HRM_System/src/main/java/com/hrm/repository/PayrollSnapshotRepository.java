package com.hrm.repository;

import com.hrm.model.PayrollSnapshot;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PayrollSnapshotRepository extends JpaRepository<PayrollSnapshot, Long> {
    void deleteByPayrollDetailIdIn(List<Long> payrollDetailIds);
}