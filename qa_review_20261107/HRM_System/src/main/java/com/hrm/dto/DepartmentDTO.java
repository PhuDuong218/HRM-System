package com.hrm.dto;

public class DepartmentDTO {
    
    private Long departmentId;
    private String departmentCode;
    private String departmentName;
    private String description;
    private Boolean isActive;
    private Long headEmployeeId;
    private String headEmployeeCode;
    private String headFullName;
    private java.time.LocalDate headEffectiveFrom;

    public Long getHeadEmployeeId() { return headEmployeeId; }
    public void setHeadEmployeeId(Long value) { headEmployeeId=value; }
    public String getHeadEmployeeCode() { return headEmployeeCode; }
    public void setHeadEmployeeCode(String value) { headEmployeeCode=value; }
    public String getHeadFullName() { return headFullName; }
    public void setHeadFullName(String value) { headFullName=value; }
    public java.time.LocalDate getHeadEffectiveFrom() { return headEffectiveFrom; }
    public void setHeadEffectiveFrom(java.time.LocalDate value) { headEffectiveFrom=value; }

    // --- GETTERS & SETTERS ---
    // (Sử dụng Generate Getters and Setters của VS Code cho 5 biến này)

    public Long getDepartmentId() {
        return departmentId;
    }

    public void setDepartmentId(Long departmentId) {
        this.departmentId = departmentId;
    }

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
}
