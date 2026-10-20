package com.hrm.model;

// import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
// import jakarta.persistence.Version;

@Entity
@Table(name = "departments")
public class Department extends BaseEntity { // Kế thừa toàn bộ cột từ BaseEntity

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "department_id")
    private Long departmentId;

    @Column(name = "department_code", unique = true, nullable = false, length = 50)
    private String departmentCode;

    @Column(name = "department_name", nullable = false, length = 200)
    private String departmentName;

    @Column(name = "description", length = 1000)
    private String description;

    @Column(name = "is_active", nullable = false)
    private Boolean isActive;

    // --- CONSTRUCTORS ---
    public Department() {
    }

    // public Department(String departmentCode, String departmentName, String description, Boolean isActive, LocalDateTime createdAt, LocalDateTime updatedAt, Long createdBy, Long updatedBy) {
    //     this.departmentCode = departmentCode;
    //     this.departmentName = departmentName;
    //     this.description = description;
    //     this.isActive = isActive;
    //     this.createdAt = createdAt;
    //     this.updatedAt = updatedAt;
    //     this.createdBy = createdBy;
    //     this.updatedBy = updatedBy;
    // }

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
