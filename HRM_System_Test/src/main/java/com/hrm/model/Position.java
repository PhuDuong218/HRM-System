package com.hrm.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "positions")
public class Position extends BaseEntity { // Kế thừa toàn bộ cột từ BaseEntity

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "position_id")
    private Long positionId;

    @Column(name = "position_code", unique = true, nullable = false, length = 50)
    private String positionCode;

    @Column(name = "position_name", nullable = false, length = 200)
    private String positionName;

    @Column(name = "description", length = 1000)
    private String description;

    @Column(name = "is_active", nullable = false)
    private Boolean isActive;

    // --- CONSTRUCTORS ---
    public Position() {
    }

    // public Position(String positionCode, String positionName, String description, Boolean isActive) {
    //     this.positionCode = positionCode;
    //     this.positionName = positionName;
    //     this.description = description;
    //     this.isActive = isActive;
    // }

    public Long getPositionId() {
        return positionId;
    }

    public String getPositionCode() {
        return positionCode;
    }

    public void setPositionCode(String positionCode) {
        this.positionCode = positionCode;
    }

    public String getPositionName() {
        return positionName;
    }

    public void setPositionName(String positionName) {
        this.positionName = positionName;
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
    


    

