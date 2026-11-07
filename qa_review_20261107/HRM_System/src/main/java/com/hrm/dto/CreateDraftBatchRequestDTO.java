package com.hrm.dto;

import jakarta.validation.constraints.NotNull;

public class CreateDraftBatchRequestDTO {

    @NotNull(message = "ID kỳ lương không được để trống!")
    private Long periodId;

    public Long getPeriodId() {
        return periodId;
    }

    public void setPeriodId(Long periodId) {
        this.periodId = periodId;
    }
}