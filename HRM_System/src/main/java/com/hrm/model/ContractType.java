package com.hrm.model;

import jakarta.persistence.*;

@Entity
@Table(name = "contract_types", schema = "dbo")
public class ContractType extends BaseEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "contract_type_id")
    private Long contractTypeId;

    @Column(name = "type_code", nullable = false, length = 50, unique = true)
    private String typeCode;

    @Column(name = "type_name", nullable = false, length = 200)
    private String typeName;

    @Column(name = "is_active", nullable = false)
    private Boolean isActive;

    @Enumerated(EnumType.STRING)
    @Column(name="term_kind", nullable=false, length=20)
    private com.hrm.enums.ContractTermKind termKind;
    @Column(name="max_months") private Integer maxMonths;
    public com.hrm.enums.ContractTermKind getTermKind(){return termKind;}
    public void setTermKind(com.hrm.enums.ContractTermKind value){termKind=value;}
    public Integer getMaxMonths(){return maxMonths;}
    public void setMaxMonths(Integer value){maxMonths=value;}
    public ContractType() {}
    public Long getContractTypeId() { return contractTypeId; }
    public String getTypeCode() { return typeCode; }
    public void setTypeCode(String value) { typeCode = value; }
    public String getTypeName() { return typeName; }
    public void setTypeName(String value) { typeName = value; }
    public Boolean getIsActive() { return isActive; }
    public void setIsActive(Boolean value) { isActive = value; }
}