package com.hrm.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "payslips")
public class Payslip extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "payslip_id")
    private Long payslipId;

    @Column(name = "payroll_detail_id", unique = true, nullable = false)
    private Long payrollDetailId;

    @Column(name = "payslip_number", unique = true, nullable = false, length = 50)
    private String payslipNumber;

    @Column(name = "issued_at", nullable = false)
    private LocalDateTime issuedAt = LocalDateTime.now();

    @Column(name = "file_reference", length = 1000)
    private String fileReference;

    public Payslip() {}

    public Payslip(Long payrollDetailId, String payslipNumber, LocalDateTime issuedAt, String fileReference) {
        this.payrollDetailId = payrollDetailId;
        this.payslipNumber = payslipNumber;
        this.issuedAt = issuedAt != null ? issuedAt : LocalDateTime.now();
        this.fileReference = fileReference;
    }

    public Long getPayslipId() { return payslipId; }
    public void setPayslipId(Long payslipId) { this.payslipId = payslipId; }
    public Long getPayrollDetailId() { return payrollDetailId; }
    public void setPayrollDetailId(Long payrollDetailId) { this.payrollDetailId = payrollDetailId; }
    public String getPayslipNumber() { return payslipNumber; }
    public void setPayslipNumber(String payslipNumber) { this.payslipNumber = payslipNumber; }
    public LocalDateTime getIssuedAt() { return issuedAt; }
    public void setIssuedAt(LocalDateTime issuedAt) { this.issuedAt = issuedAt; }
    public String getFileReference() { return fileReference; }
    public void setFileReference(String fileReference) { this.fileReference = fileReference; }
}