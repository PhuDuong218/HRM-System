package com.hrm.service;

import com.hrm.dto.AdjustmentBatchRequestDTO;
import com.hrm.dto.CreateDraftBatchRequestDTO;
import com.hrm.dto.PayrollBatchDTO;
import com.hrm.enums.BatchStatus;
import com.hrm.model.PayrollBatch;
import com.hrm.repository.PayrollBatchRepository;
import com.hrm.repository.PayrollDetailRepository;
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
    private final PayrollDetailRepository detailRepository;
    private final PayslipService payslipService;

    // Sử dụng account_id = 2L (admin.sample) có sẵn trong CSDL làm người thực hiện mặc định
    private static final Long DEFAULT_ACTOR_ACCOUNT_ID = 2L;

    public PayrollBatchService(PayrollBatchRepository batchRepository,
                               PayrollPeriodRepository periodRepository,
                               PayrollDetailRepository detailRepository,
                               PayslipService payslipService) {
        this.batchRepository = batchRepository;
        this.periodRepository = periodRepository;
        this.detailRepository = detailRepository;
        this.payslipService = payslipService;
    }

    public List<PayrollBatchDTO> getAllBatches() {
        return batchRepository.findAll().stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }

    public List<PayrollBatchDTO> getBatchesByPeriod(Long periodId) {
        return batchRepository.findByPeriodId(periodId).stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }

    // P-05: Tạo bản nháp bảng lương theo kỳ
    @Transactional
    public PayrollBatchDTO createDraftBatch(CreateDraftBatchRequestDTO request) {
        Long periodId = request.getPeriodId();
        if (!periodRepository.existsById(periodId)) {
            throw new RuntimeException("Kỳ lương với ID " + periodId + " không tồn tại!");
        }

        if (batchRepository.existsByPeriodIdAndStatus(periodId, BatchStatus.NHAP)) {
            throw new RuntimeException("Kỳ lương này đã có một bản ghi NHÁP đang xử lý. Vui lòng không tạo trùng!");
        }

        int nextVersion = 1;
        var latestBatchOpt = batchRepository.findFirstByPeriodIdOrderByVersionNoDesc(periodId);
        if (latestBatchOpt.isPresent()) {
            nextVersion = latestBatchOpt.get().getVersionNo() + 1;
        }

        PayrollBatch batch = new PayrollBatch();
        batch.setPeriodId(periodId);
        batch.setVersionNo(nextVersion);
        batch.setStatus(BatchStatus.NHAP);
        batch.setCreatedAt(LocalDateTime.now());

        PayrollBatch saved = batchRepository.save(batch);
        return toDTO(saved);
    }

    // P-12: Chốt bảng lương (Khóa cứng dữ liệu theo đúng CK_batches_lock)
    @Transactional
    public PayrollBatchDTO lockBatch(Long batchId, Long actorAccountId) {
        PayrollBatch batch = batchRepository.findById(batchId)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy bảng lương ID: " + batchId));

        if (batch.getStatus() != BatchStatus.NHAP) {
            throw new RuntimeException("Chỉ có bảng lương ở trạng thái NHAP mới được phép Chốt!");
        }

        if (detailRepository.findByBatchId(batchId).isEmpty()) {
            throw new RuntimeException("Bảng lương chưa được chạy tính toán dữ liệu, không thể chốt!");
        }

        Long actor = actorAccountId != null ? actorAccountId : DEFAULT_ACTOR_ACCOUNT_ID;

        // Trạng thái chuẩn trong DB là DA_CHOT, locked_by và locked_at bắt buộc NOT NULL
        batch.setStatus(BatchStatus.DA_CHOT);
        batch.setLockedBy(actor);
        batch.setLockedAt(LocalDateTime.now());
        batch.setUpdatedAt(LocalDateTime.now());

        PayrollBatch saved = batchRepository.save(batch);
        return toDTO(saved);
    }

    // P-12 & P-14: Công bố bảng lương và tự động phát hành phiếu lương (theo đúng CK_batches_publication)
    @Transactional
    public PayrollBatchDTO publishBatch(Long batchId, Long actorAccountId) {
        PayrollBatch batch = batchRepository.findById(batchId)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy bảng lương ID: " + batchId));

        if (batch.getStatus() != BatchStatus.DA_CHOT) {
            throw new RuntimeException("Bảng lương phải ở trạng thái DA_CHOT trước khi Công bố!");
        }

        if (batch.getPublishedAt() != null) {
            throw new RuntimeException("Bảng lương này đã được công bố trước đó rồi!");
        }

        Long actor = actorAccountId != null ? actorAccountId : DEFAULT_ACTOR_ACCOUNT_ID;
        LocalDateTime now = LocalDateTime.now();
        if (batch.getLockedAt() != null && now.isBefore(batch.getLockedAt())) {
            now = batch.getLockedAt().plusSeconds(1);
        }

        // Theo DB constraint: Khi công bố, status vẫn giữ DA_CHOT, cập nhật published_by và published_at
        batch.setPublishedBy(actor);
        batch.setPublishedAt(now);
        batch.setUpdatedAt(now);

        PayrollBatch saved = batchRepository.save(batch);

        // P-14: Tự động kích hoạt phát hành phiếu lương cho nhân viên
        payslipService.generatePayslipsForBatch(batchId);

        return toDTO(saved);
    }

    // P-13: Tạo phiên bản điều chỉnh có lý do, liên kết batch cũ
    @Transactional
    public PayrollBatchDTO createAdjustmentBatch(Long batchId, AdjustmentBatchRequestDTO request) {
        PayrollBatch oldBatch = batchRepository.findById(batchId)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy bảng lương gốc ID: " + batchId));

        if (oldBatch.getStatus() != BatchStatus.DA_CHOT) {
            throw new RuntimeException("Chỉ có thể tạo bản điều chỉnh từ bảng lương đã chốt (DA_CHOT)!");
        }

        Long periodId = oldBatch.getPeriodId();
        if (batchRepository.existsByPeriodIdAndStatus(periodId, BatchStatus.NHAP)) {
            throw new RuntimeException("Kỳ lương này đang có một bản nháp khác đang xử lý. Vui lòng hoàn thành bản nháp đó trước!");
        }

        int nextVersion = oldBatch.getVersionNo() + 1;
        var maxVersionBatch = batchRepository.findFirstByPeriodIdOrderByVersionNoDesc(periodId);
        if (maxVersionBatch.isPresent() && maxVersionBatch.get().getVersionNo() >= nextVersion) {
            nextVersion = maxVersionBatch.get().getVersionNo() + 1;
        }

        PayrollBatch adjustmentBatch = new PayrollBatch();
        adjustmentBatch.setPeriodId(periodId);
        adjustmentBatch.setVersionNo(nextVersion);
        adjustmentBatch.setPreviousBatchId(oldBatch.getBatchId()); // Khóa ngoại trỏ về bản cũ
        adjustmentBatch.setAdjustmentReason(request.getAdjustmentReason().trim()); // Bắt buộc lý do
        adjustmentBatch.setStatus(BatchStatus.NHAP);
        adjustmentBatch.setCreatedAt(LocalDateTime.now());

        PayrollBatch saved = batchRepository.save(adjustmentBatch);
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