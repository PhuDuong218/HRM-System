package com.hrm.model;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "employee_qualifications")
public class EmployeeQualification extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "employee_qualification_id")
    private Long employeeQualificationId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "employee_id", nullable = false)
    private Employee employee;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "qualification_level_id", nullable = false)
    private QualificationLevel qualificationLevel;

    @Column(name = "major", length = 200)
    private String major;

    @Column(name = "institution", length = 300)
    private String institution;

    @Column(name = "graduation_year")
    private Integer graduationYear;

    @Column(name = "certificate_number", length = 100)
    private String certificateNumber;

    @Column(name = "is_primary", nullable = false)
    private Boolean isPrimary = false;

    // Lưu trạng thái xóa mềm; không xóa vật lý bản ghi.
    @Column(name = "is_deleted", nullable = false)
    private Boolean isDeleted = false;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    @Column(name = "deleted_by")
    private Long deletedBy;
    public Long getEmployeeQualificationId() { return employeeQualificationId; }
    public void setEmployeeQualificationId(Long value) { this.employeeQualificationId = value; }
    public Employee getEmployee() { return employee; }
    public void setEmployee(Employee value) { this.employee = value; }
    public QualificationLevel getQualificationLevel() { return qualificationLevel; }
    public void setQualificationLevel(QualificationLevel value) { this.qualificationLevel = value; }
    public String getMajor() { return major; }
    public void setMajor(String value) { this.major = value; }
    public String getInstitution() { return institution; }
    public void setInstitution(String value) { this.institution = value; }
    public Integer getGraduationYear() { return graduationYear; }
    public void setGraduationYear(Integer value) { this.graduationYear = value; }
    public String getCertificateNumber() { return certificateNumber; }
    public void setCertificateNumber(String value) { this.certificateNumber = value; }
    public Boolean getIsPrimary() { return isPrimary; }
    public void setIsPrimary(Boolean value) { this.isPrimary = value; }
    public Boolean getIsDeleted() { return isDeleted; }
    public void setIsDeleted(Boolean value) { this.isDeleted = value; }
    public LocalDateTime getDeletedAt() { return deletedAt; }
    public void setDeletedAt(LocalDateTime value) { this.deletedAt = value; }
    public Long getDeletedBy() { return deletedBy; }
    public void setDeletedBy(Long value) { this.deletedBy = value; }
}
