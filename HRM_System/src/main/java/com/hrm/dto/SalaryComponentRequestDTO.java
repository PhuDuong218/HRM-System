package com.hrm.dto;

import com.hrm.enums.SalaryComponentDirection;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public class SalaryComponentRequestDTO {

    @NotBlank(message = "Mã thành phần lương không được để trống!")
    @Size(max = 50, message = "Mã thành phần lương tối đa 50 ký tự!")
    private String componentCode;

    @NotBlank(message = "Tên thành phần lương không được để trống!")
    @Size(max = 200, message = "Tên thành phần lương tối đa 200 ký tự!")
    private String componentName;

    @NotNull(message = "Chiều tính (ADD/DEDUCT) không được để trống!")
    private SalaryComponentDirection direction;

    private Boolean isActive = true;

    public String getComponentCode() { return componentCode; }
    public void setComponentCode(String componentCode) { this.componentCode = componentCode; }

    public String getComponentName() { return componentName; }
    public void setComponentName(String componentName) { this.componentName = componentName; }

    public SalaryComponentDirection getDirection() { return direction; }
    public void setDirection(SalaryComponentDirection direction) { this.direction = direction; }

    public Boolean getIsActive() { return isActive; }
    public void setIsActive(Boolean isActive) { this.isActive = isActive; }
}