package com.hrm.dto;

import java.time.LocalDateTime;

public class PayslipDTO {
    private Long payslipId;
    private Long payrollDetailId;
    private String payslipNumber;
    private LocalDateTime issuedAt;
    private String fileReference;

    public PayslipDTO() {}

    public PayslipDTO(Long payslipId, Long payrollDetailId, String payslipNumber, LocalDateTime issuedAt, String fileReference) {
        this.payslipId = payslipId;
        this.payrollDetailId = payrollDetailId;
        this.payslipNumber = payslipNumber;
        this.issuedAt = issuedAt;
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