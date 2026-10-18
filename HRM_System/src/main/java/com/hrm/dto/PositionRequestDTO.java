package com.hrm.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

// Du lieu client gui len khi them / sua chuc vu
public class PositionRequestDTO {

    @NotBlank(message = "Ma chuc vu khong duoc de trong")
    @Size(max = 50, message = "Ma chuc vu khong duoc qua 50 ky tu")
    private String positionCode;

    @NotBlank(message = "Ten chuc vu khong duoc de trong")
    @Size(max = 200, message = "Ten chuc vu khong duoc qua 200 ky tu")
    private String positionName;

    @Size(max = 1000, message = "Mo ta khong duoc qua 1000 ky tu")
    private String description;

    // Them moi: he thong mac dinh true. Cap nhat: gui true/false de thay doi.
    private Boolean isActive;

    public String getPositionCode() {
        return positionCode;
    }

    public void setPositionCode(String positionCode) {
        this.positionCode = positionCode;
    }

    public String getPositionName() {
        return positionName;
    }

    public void setPositionName(String positionName) {
        this.positionName = positionName;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public Boolean getIsActive() {
        return isActive;
    }

    public void setIsActive(Boolean isActive) {
        this.isActive = isActive;
    }
}
