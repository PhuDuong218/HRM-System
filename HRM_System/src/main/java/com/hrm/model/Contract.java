
package com.hrm.model;

import java.time.LocalDate;

import com.hrm.enums.ContractStatus;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "contracts", schema = "dbo")
public class Contract extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "contract_id")
    private Long contractId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "employee_id", nullable = false)
    private Employee employee;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "contract_type_id", nullable = false)
    private ContractType contractType;

    @Column(name = "contract_number", nullable = false, length = 50, unique = true)
    private String contractNumber;

    @Column(name = "signed_date", nullable = false)
    private LocalDate signedDate;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date")
    private LocalDate endDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private ContractStatus status;

    public Contract() {
    }

    public Long getContractId() {
        return contractId;
    }

    public Employee getEmployee() {
        return employee;
    }

    public void setEmployee(Employee value) {
        employee = value;
    }

    public ContractType getContractType() {
        return contractType;
    }

    public void setContractType(ContractType value) {
        contractType = value;
    }

    public String getContractNumber() {
        return contractNumber;
    }

    public void setContractNumber(String value) {
        contractNumber = value;
    }

    public LocalDate getSignedDate() {
        return signedDate;
    }

    public void setSignedDate(LocalDate value) {
        signedDate = value;
    }

    public LocalDate getStartDate() {
        return startDate;
    }

    public void setStartDate(LocalDate value) {
        startDate = value;
    }

    public LocalDate getEndDate() {
        return endDate;
    }

    public void setEndDate(LocalDate value) {
        endDate = value;
    }

    public ContractStatus getStatus() {
        return status;
    }

    public void setStatus(ContractStatus value) {
        status = value;
    }
}
