package com.hrm.repository;

import com.hrm.enums.BatchStatus;
import com.hrm.model.PayrollBatch;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PayrollBatchRepository extends JpaRepository<PayrollBatch, Long> {

    // Lấy tất cả các phiên bản của một kỳ lương
    List<PayrollBatch> findByPeriodId(Long periodId);

    // Kiểm tra xem kỳ này đã có bảng lương nháp (NHAP) chưa
    boolean existsByPeriodIdAndStatus(Long periodId, BatchStatus status);

    // Tìm phiên bản mới nhất theo kỳ lương để tăng version_no
    Optional<PayrollBatch> findFirstByPeriodIdOrderByVersionNoDesc(Long periodId);

    // Kiểm tra xem đã tồn tại đúng cặp periodId và versionNo chưa
    boolean existsByPeriodIdAndVersionNo(Long periodId, Integer versionNo);
}