package com.hrm.dto;

import java.time.LocalDate;

public class EmployeeDTO {

    // --- THÔNG TIN CÁ NHÂN CƠ BẢN ---
    private Long employeeId;
    private String employeeCode;
    private String fullName;
    private LocalDate birthDate;
    private String email;
    private String phone;
    private LocalDate hireDate;
    private String employmentStatus; // DANG_LAM hoặc DA_NGHI

    // --- THÔNG TIN CÔNG TÁC HIỆN TẠI (Lấy từ bảng employee_assignments) ---
    // Thiết kế phẳng (Flatten) để Front-end dễ hiển thị trên Table
    private Long currentDepartmentId;
    private String currentDepartmentName;
    private Long currentPositionId;
    private String currentPositionName;

    public Long getEmployeeId() {
        return employeeId;
    }

    public void setEmployeeId(Long employeeId) {
        this.employeeId = employeeId;
    }

    public String getEmployeeCode() {
        return employeeCode;
    }

    public void setEmployeeCode(String employeeCode) {
        this.employeeCode = employeeCode;
    }

    public String getFullName() {
        return fullName;
    }

    public void setFullName(String fullName) {
        this.fullName = fullName;
    }

    public LocalDate getBirthDate() {
        return birthDate;
    }

    public void setBirthDate(LocalDate birthDate) {
        this.birthDate = birthDate;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public LocalDate getHireDate() {
        return hireDate;
    }

    public void setHireDate(LocalDate hireDate) {
        this.hireDate = hireDate;
    }

    public String getEmploymentStatus() {
        return employmentStatus;
    }

    public void setEmploymentStatus(String employmentStatus) {
        this.employmentStatus = employmentStatus;
    }

    public Long getCurrentDepartmentId() {
        return currentDepartmentId;
    }

    public void setCurrentDepartmentId(Long currentDepartmentId) {
        this.currentDepartmentId = currentDepartmentId;
    }

    public String getCurrentDepartmentName() {
        return currentDepartmentName;
    }

    public void setCurrentDepartmentName(String currentDepartmentName) {
        this.currentDepartmentName = currentDepartmentName;
    }

    public Long getCurrentPositionId() {
        return currentPositionId;
    }

    public void setCurrentPositionId(Long currentPositionId) {
        this.currentPositionId = currentPositionId;
    }

    public String getCurrentPositionName() {
        return currentPositionName;
    }

    public void setCurrentPositionName(String currentPositionName) {
        this.currentPositionName = currentPositionName;
    }

}
