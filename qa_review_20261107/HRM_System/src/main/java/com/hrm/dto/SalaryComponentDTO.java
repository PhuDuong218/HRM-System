package com.hrm.dto;

import com.hrm.enums.SalaryComponentDirection;

public class SalaryComponentDTO {
    private Long componentId;
    private String componentCode;
    private String componentName;
    private SalaryComponentDirection direction;
    private Boolean isActive;

    public SalaryComponentDTO() {}

    public SalaryComponentDTO(Long componentId, String componentCode, String componentName, SalaryComponentDirection direction, Boolean isActive) {
        this.componentId = componentId;
        this.componentCode = componentCode;
        this.componentName = componentName;
        this.direction = direction;
        this.isActive = isActive;
    }

    public Long getComponentId() { return componentId; }
    public void setComponentId(Long componentId) { this.componentId = componentId; }

    public String getComponentCode() { return componentCode; }
    public void setComponentCode(String componentCode) { this.componentCode = componentCode; }

    public String getComponentName() { return componentName; }
    public void setComponentName(String componentName) { this.componentName = componentName; }

    public SalaryComponentDirection getDirection() { return direction; }
    public void setDirection(SalaryComponentDirection direction) { this.direction = direction; }

    public Boolean getIsActive() { return isActive; }
    public void setIsActive(Boolean isActive) { this.isActive = isActive; }
}