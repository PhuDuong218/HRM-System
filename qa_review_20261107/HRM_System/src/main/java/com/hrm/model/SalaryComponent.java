package com.hrm.model;

import com.hrm.enums.SalaryComponentDirection;
import jakarta.persistence.*;

@Entity
@Table(name = "salary_components")
public class SalaryComponent extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "component_id")
    private Long componentId;

    @Column(name = "component_code", unique = true, nullable = false, length = 50)
    private String componentCode;

    @Column(name = "component_name", nullable = false, length = 200)
    private String componentName;

    @Enumerated(EnumType.STRING)
    @Column(name = "direction", nullable = false, length = 10)
    private SalaryComponentDirection direction;

    @Column(name = "is_active", nullable = false)
    private Boolean isActive = true;

    // --- CONSTRUCTORS ---
    public SalaryComponent() {
    }

    public SalaryComponent(String componentCode, String componentName, SalaryComponentDirection direction, Boolean isActive) {
        this.componentCode = componentCode;
        this.componentName = componentName;
        this.direction = direction;
        this.isActive = isActive != null ? isActive : true;
    }

    // --- GETTERS & SETTERS ---
    public Long getComponentId() {
        return componentId;
    }

    public void setComponentId(Long componentId) {
        this.componentId = componentId;
    }

    public String getComponentCode() {
        return componentCode;
    }

    public void setComponentCode(String componentCode) {
        this.componentCode = componentCode;
    }

    public String getComponentName() {
        return componentName;
    }

    public void setComponentName(String componentName) {
        this.componentName = componentName;
    }

    public SalaryComponentDirection getDirection() {
        return direction;
    }

    public void setDirection(SalaryComponentDirection direction) {
        this.direction = direction;
    }

    public Boolean getIsActive() {
        return isActive;
    }

    public void setIsActive(Boolean isActive) {
        this.isActive = isActive;
    }
}