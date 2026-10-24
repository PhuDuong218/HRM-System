package com.hrm.service;

import com.hrm.dto.CreateDraftBatchRequestDTO;
import com.hrm.dto.PayrollBatchDTO;
import com.hrm.enums.BatchStatus;
import com.hrm.model.PayrollBatch;
import com.hrm.repository.PayrollBatchRepository;
import com.hrm.repository.PayrollPeriodRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class PayrollBatchService {

    private final PayrollBatchRepository batchRepository;
    private final PayrollPeriodRepository periodRepository;

    public PayrollBatchService(PayrollBatchRepository batchRepository, PayrollPeriodRepository periodRepository) {
        this.batchRepository = batchRepository;
        this.periodRepository = periodRepository;
    }

    // 1. Lấy tất cả các đợt bảng lương
    public List<PayrollBatchDTO> getAllBatches() {
        return batchRepository.findAll().stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }

    // 2. Lấy các đợt bảng lương theo kỳ
    public List<PayrollBatchDTO> getBatchesByPeriod(Long periodId) {
        return batchRepository.findByPeriodId(periodId).stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }

    // 3. P-05: Tạo bản nháp bảng lương theo kỳ (chặn trùng phiên bản)
    @Transactional
    public PayrollBatchDTO createDraftBatch(CreateDraftBatchRequestDTO request) {
        Long periodId = request.getPeriodId();

        // Kiểm tra kỳ lương có tồn tại không
        if (!periodRepository.existsById(periodId)) {
            throw new RuntimeException("Kỳ lương với ID " + periodId + " không tồn tại!");
        }

        // Quy tắc P-05: Một kỳ lương chỉ được phép có duy nhất 1 bản ghi đang ở trạng thái NHAP
        if (batchRepository.existsByPeriodIdAndStatus(periodId, BatchStatus.NHAP)) {
            throw new RuntimeException("Kỳ lương này đã có một bản ghi NHÁP đang xử lý. Vui lòng không tạo trùng!");
        }

        // Xác định số phiên bản (version_no)
        int nextVersion = 1;
        var latestBatchOpt = batchRepository.findFirstByPeriodIdOrderByVersionNoDesc(periodId);
        if (latestBatchOpt.isPresent()) {
            nextVersion = latestBatchOpt.get().getVersionNo() + 1;
        }

        // Tạo bản nháp mới
        PayrollBatch batch = new PayrollBatch();
        batch.setPeriodId(periodId);
        batch.setVersionNo(nextVersion);
        batch.setStatus(BatchStatus.NHAP);
        batch.setCreatedAt(LocalDateTime.now());

        PayrollBatch saved = batchRepository.save(batch);
        return toDTO(saved);
    }

    private PayrollBatchDTO toDTO(PayrollBatch entity) {
        return new PayrollBatchDTO(
                entity.getBatchId(),
                entity.getPeriodId(),
                entity.getVersionNo(),
                entity.getPreviousBatchId(),
                entity.getStatus(),
                entity.getAdjustmentReason(),
                entity.getLockedBy(),
                entity.getLockedAt(),
                entity.getPublishedBy(),
                entity.getPublishedAt()
        );
    }
}