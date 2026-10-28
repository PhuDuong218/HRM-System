package com.hrm.repository;

import com.hrm.model.PayrollDetail;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PayrollDetailRepository extends JpaRepository<PayrollDetail, Long> {
    List<PayrollDetail> findByBatchId(Long batchId);
    Optional<PayrollDetail> findByBatchIdAndEmployeeId(Long batchId, Long employeeId);
    void deleteByBatchId(Long batchId);
}