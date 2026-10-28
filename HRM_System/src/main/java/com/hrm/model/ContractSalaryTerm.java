package com.hrm.model;

import java.math.BigDecimal;
import java.time.LocalDate;
import com.hrm.enums.PayBasis;
import jakarta.persistence.*;

@Entity
@Table(name="contract_salary_terms", schema="dbo")
public class ContractSalaryTerm extends BaseEntity {
    @Id
    @GeneratedValue(strategy=GenerationType.IDENTITY)
    @Column(name="salary_term_id")
    private Long salaryTermId;

    @ManyToOne(fetch=FetchType.LAZY, optional=false)
    @JoinColumn(name="contract_id", nullable=false)
    private Contract contract;

    @Column(name="effective_from", nullable=false)
    private LocalDate effectiveFrom;
    @Column(name="effective_to")
    private LocalDate effectiveTo;
    @Column(name="base_salary", nullable=false, precision=19, scale=4)
    private BigDecimal baseSalary;
    @Enumerated(EnumType.STRING)
    @Column(name="pay_basis", nullable=false, length=10)
    private PayBasis payBasis;
    @Column(name="document_reference", length=500)
    private String documentReference;

    public ContractSalaryTerm() {}
    public Long getSalaryTermId() { return salaryTermId; }
    public Contract getContract() { return contract; }
    public void setContract(Contract value) { contract=value; }
    public LocalDate getEffectiveFrom() { return effectiveFrom; }
    public void setEffectiveFrom(LocalDate value) { effectiveFrom=value; }
    public LocalDate getEffectiveTo() { return effectiveTo; }
    public void setEffectiveTo(LocalDate value) { effectiveTo=value; }
    public BigDecimal getBaseSalary() { return baseSalary; }
    public void setBaseSalary(BigDecimal value) { baseSalary=value; }
    public PayBasis getPayBasis() { return payBasis; }
    public void setPayBasis(PayBasis value) { payBasis=value; }
    public String getDocumentReference() { return documentReference; }
    public void setDocumentReference(String value) { documentReference=value; }
}