package com.hrm.model;

import java.math.BigDecimal;
import java.time.LocalDate;
import com.hrm.enums.AllowanceCalculationBasis;
import jakarta.persistence.*;

@Entity
@Table(name="employee_allowances",schema="dbo")
public class EmployeeAllowance extends BaseEntity {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY)
    @Column(name="employee_allowance_id") private Long employeeAllowanceId;
    @ManyToOne(fetch=FetchType.LAZY,optional=false)
    @JoinColumn(name="employee_id",nullable=false) private Employee employee;
    @ManyToOne(fetch=FetchType.LAZY,optional=false)
    @JoinColumn(name="allowance_type_id",nullable=false) private AllowanceType allowanceType;
    @Column(name="effective_from",nullable=false) private LocalDate effectiveFrom;
    @Column(name="effective_to") private LocalDate effectiveTo;
    @Enumerated(EnumType.STRING)
    @Column(name="calculation_basis",nullable=false,length=20) private AllowanceCalculationBasis calculationBasis;
    @Column(name="amount",nullable=false,precision=19,scale=4) private BigDecimal amount;
    @Column(name="condition_note",length=1000) private String conditionNote;

    public EmployeeAllowance() {}
    public Long getEmployeeAllowanceId() { return employeeAllowanceId; }
    public Employee getEmployee() { return employee; }
    public void setEmployee(Employee v) { employee=v; }
    public AllowanceType getAllowanceType() { return allowanceType; }
    public void setAllowanceType(AllowanceType v) { allowanceType=v; }
    public LocalDate getEffectiveFrom() { return effectiveFrom; }
    public void setEffectiveFrom(LocalDate v) { effectiveFrom=v; }
    public LocalDate getEffectiveTo() { return effectiveTo; }
    public void setEffectiveTo(LocalDate v) { effectiveTo=v; }
    public AllowanceCalculationBasis getCalculationBasis() { return calculationBasis; }
    public void setCalculationBasis(AllowanceCalculationBasis v) { calculationBasis=v; }
    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal v) { amount=v; }
    public String getConditionNote() { return conditionNote; }
    public void setConditionNote(String v) { conditionNote=v; }
}