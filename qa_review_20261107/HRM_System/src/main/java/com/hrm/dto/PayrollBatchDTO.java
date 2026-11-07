package com.hrm.dto;

import com.hrm.enums.BatchStatus;
import java.time.LocalDateTime;

public class PayrollBatchDTO {
    private Long batchId;
    private Long periodId;
    private Integer versionNo;
    private Long previousBatchId;
    private BatchStatus status;
    private String adjustmentReason;
    private Long lockedBy;
    private LocalDateTime lockedAt;
    private Long publishedBy;
    private LocalDateTime publishedAt;

    public PayrollBatchDTO() {}

    public PayrollBatchDTO(Long batchId, Long periodId, Integer versionNo, Long previousBatchId,
                           BatchStatus status, String adjustmentReason, Long lockedBy,
                           LocalDateTime lockedAt, Long publishedBy, LocalDateTime publishedAt) {
        this.batchId = batchId;
        this.periodId = periodId;
        this.versionNo = versionNo;
        this.previousBatchId = previousBatchId;
        this.status = status;
        this.adjustmentReason = adjustmentReason;
        this.lockedBy = lockedBy;
        this.lockedAt = lockedAt;
        this.publishedBy = publishedBy;
        this.publishedAt = publishedAt;
    }

    public Long getBatchId() { return batchId; }
    public void setBatchId(Long batchId) { this.batchId = batchId; }

    public Long getPeriodId() { return periodId; }
    public void setPeriodId(Long periodId) { this.periodId = periodId; }

    public Integer getVersionNo() { return versionNo; }
    public void setVersionNo(Integer versionNo) { this.versionNo = versionNo; }

    public Long getPreviousBatchId() { return previousBatchId; }
    public void setPreviousBatchId(Long previousBatchId) { this.previousBatchId = previousBatchId; }

    public BatchStatus getStatus() { return status; }
    public void setStatus(BatchStatus status) { this.status = status; }

    public String getAdjustmentReason() { return adjustmentReason; }
    public void setAdjustmentReason(String adjustmentReason) { this.adjustmentReason = adjustmentReason; }

    public Long getLockedBy() { return lockedBy; }
    public void setLockedBy(Long lockedBy) { this.lockedBy = lockedBy; }

    public LocalDateTime getLockedAt() { return lockedAt; }
    public void setLockedAt(LocalDateTime lockedAt) { this.lockedAt = lockedAt; }

    public Long getPublishedBy() { return publishedBy; }
    public void setPublishedBy(Long publishedBy) { this.publishedBy = publishedBy; }

    public LocalDateTime getPublishedAt() { return publishedAt; }
    public void setPublishedAt(LocalDateTime publishedAt) { this.publishedAt = publishedAt; }
}