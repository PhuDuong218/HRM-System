package com.hrm.dto;

import java.math.BigDecimal;
import java.util.List;

public class EmployeeSalaryProfileDTO {
    private Long employeeId;
    private String employeeCode;
    private String fullName;
    private BigDecimal baseSalary;     // Lương cơ bản theo hợp đồng
    private String payBasis;           // THANG, NGAY, GIO
    private List<AllowanceItemDTO> allowances; // Danh sách phụ cấp hợp lệ

    public static class AllowanceItemDTO {
        private String allowanceCode;  // Ví dụ: AN_TRUA, XANG_XE
        private String allowanceName;
        private BigDecimal amount;
        private String calculationBasis; // THEO_KY, THEO_NGAY_CONG, THEO_GIO

        public AllowanceItemDTO() {}

        public AllowanceItemDTO(String allowanceCode, String allowanceName, BigDecimal amount, String calculationBasis) {
            this.allowanceCode = allowanceCode;
            this.allowanceName = allowanceName;
            this.amount = amount;
            this.calculationBasis = calculationBasis;
        }

        public String getAllowanceCode() { return allowanceCode; }
        public void setAllowanceCode(String allowanceCode) { this.allowanceCode = allowanceCode; }
        public String getAllowanceName() { return allowanceName; }
        public void setAllowanceName(String allowanceName) { this.allowanceName = allowanceName; }
        public BigDecimal getAmount() { return amount; }
        public void setAmount(BigDecimal amount) { this.amount = amount; }
        public String getCalculationBasis() { return calculationBasis; }
        public void setCalculationBasis(String calculationBasis) { this.calculationBasis = calculationBasis; }
    }

    public EmployeeSalaryProfileDTO() {}

    public Long getEmployeeId() { return employeeId; }
    public void setEmployeeId(Long employeeId) { this.employeeId = employeeId; }
    public String getEmployeeCode() { return employeeCode; }
    public void setEmployeeCode(String employeeCode) { this.employeeCode = employeeCode; }
    public String getFullName() { return fullName; }
    public void setFullName(String fullName) { this.fullName = fullName; }
    public BigDecimal getBaseSalary() { return baseSalary; }
    public void setBaseSalary(BigDecimal baseSalary) { this.baseSalary = baseSalary; }
    public String getPayBasis() { return payBasis; }
    public void setPayBasis(String payBasis) { this.payBasis = payBasis; }
    public List<AllowanceItemDTO> getAllowances() { return allowances; }
    public void setAllowances(List<AllowanceItemDTO> allowances) { this.allowances = allowances; }
}