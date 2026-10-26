package com.hrm.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class DepartmentRequestDTO {

    @NotBlank(message = "Mã phòng ban không được để trống")
    @Size(max = 50, message = "Mã phòng ban không vượt quá 50 ký tự")
    private String departmentCode;

    @NotBlank(message = "Tên phòng ban không được để trống")
    @Size(max = 200, message = "Tên phòng ban không vượt quá 200 ký tự")
    private String departmentName;

    @Size(max = 1000, message = "Mô tả không vượt quá 1000 ký tự")
    private String description;

    // Dành cho trường hợp Cập nhật (PUT) muốn đóng/mở phòng ban
    private Boolean isActive;

    @Size(max = 500, message = "Địa chỉ không vượt quá 500 ký tự")
    private String address;
    // --- GETTERS & SETTERS ---
    // (Sử dụng Generate Getters and Setters của VS Code cho 4 biến này)

    public String getDepartmentCode() {
        return departmentCode;
    }

    public void setDepartmentCode(String departmentCode) {
        this.departmentCode = departmentCode;
    }

    public String getDepartmentName() {
        return departmentName;
    }

    public void setDepartmentName(String departmentName) {
        this.departmentName = departmentName;
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

    public String getAddress() {
    return address;
}

public void setAddress(String address) {
    this.address = address;
}
}