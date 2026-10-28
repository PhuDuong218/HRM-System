package com.hrm.model;

import jakarta.persistence.*;

@Entity
@Table(name="allowance_types", schema="dbo")
public class AllowanceType extends BaseEntity {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY)
    @Column(name="allowance_type_id")
    private Long allowanceTypeId;
    @Column(name="type_code", nullable=false, unique=true, length=50)
    private String typeCode;
    @Column(name="type_name", nullable=false, length=200)
    private String typeName;
    @Column(name="description", length=1000)
    private String description;
    @Column(name="is_active", nullable=false)
    private Boolean isActive;

    public AllowanceType() {}
    public Long getAllowanceTypeId() { return allowanceTypeId; }
    public String getTypeCode() { return typeCode; }
    public void setTypeCode(String v) { typeCode=v; }
    public String getTypeName() { return typeName; }
    public void setTypeName(String v) { typeName=v; }
    public String getDescription() { return description; }
    public void setDescription(String v) { description=v; }
    public Boolean getIsActive() { return isActive; }
    public void setIsActive(Boolean v) { isActive=v; }
}