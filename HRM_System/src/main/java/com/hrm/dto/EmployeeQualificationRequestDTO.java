package com.hrm.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public class EmployeeQualificationRequestDTO {
    @NotNull(message = "Phai chon cap trinh do")
    private Long qualificationLevelId;

    @Size(max = 200)
    private String major;

    @Size(max = 300)
    private String institution;

    @Min(value = 1900, message = "Nam tot nghiep khong hop le")
    private Integer graduationYear;

    @Size(max = 100)
    private String certificateNumber;

    // POST mac dinh false. PUT null = khong doi trang thai chinh.
    private Boolean isPrimary;

    public Long getQualificationLevelId() { return qualificationLevelId; }
    public void setQualificationLevelId(Long value) { this.qualificationLevelId = value; }
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
}
