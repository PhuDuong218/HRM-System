package com.hrm.model;

import com.hrm.enums.BatchStatus;
import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "payroll_batches", uniqueConstraints = {
    @UniqueConstraint(name = "UQ_payroll_batches_period_version", columnNames = {"period_id", "version_no"})
})
public class PayrollBatch extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "batch_id")
    private Long batchId;

    @Column(name = "period_id", nullable = false)
    private Long periodId;

    @Column(name = "version_no", nullable = false)
    private Integer versionNo = 1;

    @Column(name = "previous_batch_id")
    private Long previousBatchId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private BatchStatus status = BatchStatus.NHAP;

    @Column(name = "adjustment_reason", length = 1000)
    private String adjustmentReason;

    @Column(name = "locked_by")
    private Long lockedBy;

    @Column(name = "locked_at")
    private LocalDateTime lockedAt;

    @Column(name = "published_by")
    private Long publishedBy;

    @Column(name = "published_at")
    private LocalDateTime publishedAt;

    // --- CONSTRUCTORS ---
    public PayrollBatch() {
    }

    public PayrollBatch(Long periodId, Integer versionNo, BatchStatus status) {
        this.periodId = periodId;
        this.versionNo = versionNo != null ? versionNo : 1;
        this.status = status != null ? status : BatchStatus.NHAP;
    }

    // --- GETTERS & SETTERS ---
    public Long getBatchId() {
        return batchId;
    }

    public void setBatchId(Long batchId) {
        this.batchId = batchId;
    }

    public Long getPeriodId() {
        return periodId;
    }

    public void setPeriodId(Long periodId) {
        this.periodId = periodId;
    }

    public Integer getVersionNo() {
        return versionNo;
    }

    public void setVersionNo(Integer versionNo) {
        this.versionNo = versionNo;
    }

    public Long getPreviousBatchId() {
        return previousBatchId;
    }

    public void setPreviousBatchId(Long previousBatchId) {
        this.previousBatchId = previousBatchId;
    }

    public BatchStatus getStatus() {
        return status;
    }

    public void setStatus(BatchStatus status) {
        this.status = status;
    }

    public String getAdjustmentReason() {
        return adjustmentReason;
    }

    public void setAdjustmentReason(String adjustmentReason) {
        this.adjustmentReason = adjustmentReason;
    }

    public Long getLockedBy() {
        return lockedBy;
    }

    public void setLockedBy(Long lockedBy) {
        this.lockedBy = lockedBy;
    }

    public LocalDateTime getLockedAt() {
        return lockedAt;
    }

    public void setLockedAt(LocalDateTime lockedAt) {
        this.lockedAt = lockedAt;
    }

    public Long getPublishedBy() {
        return publishedBy;
    }

    public void setPublishedBy(Long publishedBy) {
        this.publishedBy = publishedBy;
    }

    public LocalDateTime getPublishedAt() {
        return publishedAt;
    }

    public void setPublishedAt(LocalDateTime publishedAt) {
        this.publishedAt = publishedAt;
    }
}