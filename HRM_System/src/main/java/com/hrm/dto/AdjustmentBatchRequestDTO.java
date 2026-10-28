package com.hrm.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class AdjustmentBatchRequestDTO {

    @NotBlank(message = "Lý do điều chỉnh không được để trống!")
    @Size(max = 1000, message = "Lý do điều chỉnh tối đa 1000 ký tự!")
    private String adjustmentReason;

    public String getAdjustmentReason() { return adjustmentReason; }
    public void setAdjustmentReason(String adjustmentReason) { this.adjustmentReason = adjustmentReason; }
}