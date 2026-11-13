package com.hrm.service;

import com.hrm.dto.AdjustmentBatchRequestDTO;
import com.hrm.dto.CreateDraftBatchRequestDTO;
import com.hrm.dto.PayrollBatchDTO;
import com.hrm.enums.BatchStatus;
import com.hrm.exception.BusinessException;
import com.hrm.model.PayrollBatch;
import com.hrm.repository.PayrollBatchRepository;
import com.hrm.repository.PayrollDetailRepository;
import com.hrm.repository.PayrollPeriodRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class PayrollBatchService {
    @org.springframework.beans.factory.annotation.Autowired
    private PayrollReadinessGuard readiness;

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
            throw new BusinessException(HttpStatus.NOT_FOUND, "Kỳ lương với ID " + periodId + " không tồn tại!");
        }

        if (batchRepository.existsByPeriodIdAndStatus(periodId, BatchStatus.NHAP)) {
            throw new BusinessException(HttpStatus.CONFLICT, "Kỳ lương này đã có một bản ghi NHÁP đang xử lý. Vui lòng sử dụng đợt nháp hiện tại!");
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
        return lockBatch(batchId, actorAccountId, false);
    }

    @Transactional
    public PayrollBatchDTO lockBatch(Long batchId, Long actorAccountId, boolean forceDemo) {
        PayrollBatch batch = batchRepository.findForUpdate(batchId)
                .or(() -> batchRepository.findById(batchId))
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "Không tìm thấy bảng lương ID: " + batchId));

        if (batch.getStatus() != BatchStatus.NHAP) {
            throw new BusinessException(HttpStatus.CONFLICT, "Chỉ có bảng lương ở trạng thái NHÁP mới được phép Chốt!");
        }

        if (detailRepository.findByBatchId(batchId).isEmpty()) {
            throw new BusinessException(HttpStatus.CONFLICT, "Bảng lương chưa được chạy tính toán dữ liệu, không thể chốt! Vui lòng bấm 'Tính tự động' trước.");
        }

        if (!forceDemo && readiness != null) {
            readiness.requireReady(batch.getPeriodId());
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
        PayrollBatch batch = batchRepository.findForUpdate(batchId)
                .or(() -> batchRepository.findById(batchId))
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "Không tìm thấy bảng lương ID: " + batchId));

        if (batch.getStatus() != BatchStatus.DA_CHOT) {
            throw new BusinessException(HttpStatus.CONFLICT, "Bảng lương phải ở trạng thái ĐÃ CHỐT trước khi Công bố!");
        }

        if (batch.getPublishedAt() != null) {
            return toDTO(batch);
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
        PayrollBatch oldBatch = batchRepository.findForUpdate(batchId)
                .or(() -> batchRepository.findById(batchId))
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "Không tìm thấy bảng lương gốc ID: " + batchId));

        if (oldBatch.getStatus() != BatchStatus.DA_CHOT) {
            throw new BusinessException(HttpStatus.CONFLICT, "Chỉ có thể tạo bản điều chỉnh từ bảng lương đã chốt (DA_CHOT)!");
        }

        Long periodId = oldBatch.getPeriodId();
        if (batchRepository.existsByPeriodIdAndStatus(periodId, BatchStatus.NHAP)) {
            throw new BusinessException(HttpStatus.CONFLICT, "Kỳ lương này đang có một bản nháp đang xử lý. Vui lòng chuyển sang phiên bản nháp hiện có hoặc hoàn thành bản nháp đó trước!");
        }

        int nextVersion = oldBatch.getVersionNo() + 1;
        var maxVersionBatch = batchRepository.findFirstByPeriodIdOrderByVersionNoDesc(periodId);
        if (maxVersionBatch.isPresent() && maxVersionBatch.get().getVersionNo() >= nextVersion) {
            nextVersion = maxVersionBatch.get().getVersionNo() + 1;
        }

        PayrollBatch adjustmentBatch = new PayrollBatch();
        adjustmentBatch.setPeriodId(periodId);
        adjustmentBatch.setVersionNo(nextVersion);
        adjustmentBatch.setPreviousBatchId(oldBatch.getBatchId());
        adjustmentBatch.setAdjustmentReason(request.getAdjustmentReason().trim());
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
