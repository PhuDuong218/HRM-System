package com.hrm.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public class LeaveTypeRequestDTO {

    @NotBlank(message = "Mã loại nghỉ không được để trống")
    @Size(max = 50, message = "Mã loại nghỉ tối đa 50 ký tự")
    private String typeCode;

    @NotBlank(message = "Tên loại nghỉ không được để trống")
    @Size(max = 200, message = "Tên loại nghỉ tối đa 200 ký tự")
    private String typeName;

    @NotNull(message = "Phải xác định loại nghỉ có hưởng lương hay không")
    private Boolean isPaid;

    // POST mặc định true.
    // PUT nếu null thì giữ nguyên trạng thái.
    private Boolean isActive;

    public String getTypeCode() {
        return typeCode;
    }

    public void setTypeCode(String typeCode) {
        this.typeCode = typeCode;
    }

    public String getTypeName() {
        return typeName;
    }

    public void setTypeName(String typeName) {
        this.typeName = typeName;
    }

    public Boolean getIsPaid() {
        return isPaid;
    }

    public void setIsPaid(Boolean isPaid) {
        this.isPaid = isPaid;
    }

    public Boolean getIsActive() {
        return isActive;
    }

    public void setIsActive(Boolean isActive) {
        this.isActive = isActive;
    }
}