package com.hrm.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public class PayslipDetailDTO {
    private Long payslipId;
    private String payslipNumber;
    private LocalDateTime issuedAt;
    private Long employeeId;
    private String periodCode;
    private Integer versionNo;
    private BigDecimal totalGross;
    private BigDecimal totalDeduction;
    private BigDecimal netSalary;
    private List<PayrollDetailViewDTO.PayrollLineDTO> lines;

    public Long getPayslipId() { return payslipId; }
    public void setPayslipId(Long payslipId) { this.payslipId = payslipId; }
    public String getPayslipNumber() { return payslipNumber; }
    public void setPayslipNumber(String payslipNumber) { this.payslipNumber = payslipNumber; }
    public LocalDateTime getIssuedAt() { return issuedAt; }
    public void setIssuedAt(LocalDateTime issuedAt) { this.issuedAt = issuedAt; }
    public Long getEmployeeId() { return employeeId; }
    public void setEmployeeId(Long employeeId) { this.employeeId = employeeId; }
    public String getPeriodCode() { return periodCode; }
    public void setPeriodCode(String periodCode) { this.periodCode = periodCode; }
    public Integer getVersionNo() { return versionNo; }
    public void setVersionNo(Integer versionNo) { this.versionNo = versionNo; }
    public BigDecimal getTotalGross() { return totalGross; }
    public void setTotalGross(BigDecimal totalGross) { this.totalGross = totalGross; }
    public BigDecimal getTotalDeduction() { return totalDeduction; }
    public void setTotalDeduction(BigDecimal totalDeduction) { this.totalDeduction = totalDeduction; }
    public BigDecimal getNetSalary() { return netSalary; }
    public void setNetSalary(BigDecimal netSalary) { this.netSalary = netSalary; }
    public List<PayrollDetailViewDTO.PayrollLineDTO> getLines() { return lines; }
    public void setLines(List<PayrollDetailViewDTO.PayrollLineDTO> lines) { this.lines = lines; }
}