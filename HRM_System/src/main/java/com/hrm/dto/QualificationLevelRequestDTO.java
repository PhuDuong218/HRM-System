package com.hrm.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

// PUT yeu cau day du ma + ten; ma khong duoc doi sau khi tao.
public class QualificationLevelRequestDTO {
    @NotBlank(message = "Ma trinh do khong duoc de trong")
    @Size(max = 50)
    private String levelCode;

    @NotBlank(message = "Ten trinh do khong duoc de trong")
    @Size(max = 200)
    private String levelName;

    @Size(max = 1000)
    private String description;

    // POST mac dinh true. PUT bo trong thi giu nguyen.
    private Boolean isActive;

    public String getLevelCode() { return levelCode; }
    public void setLevelCode(String value) { this.levelCode = value; }
    public String getLevelName() { return levelName; }
    public void setLevelName(String value) { this.levelName = value; }
    public String getDescription() { return description; }
    public void setDescription(String value) { this.description = value; }
    public Boolean getIsActive() { return isActive; }
    public void setIsActive(Boolean value) { this.isActive = value; }
}
