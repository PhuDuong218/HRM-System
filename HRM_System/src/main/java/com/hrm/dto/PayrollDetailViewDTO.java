package com.hrm.dto;

import java.math.BigDecimal;
import java.util.List;

public class PayrollDetailViewDTO {
    private Long payrollDetailId;
    private Long employeeId;
    private BigDecimal totalGross;      // Tổng thu nhập
    private BigDecimal totalDeduction;  // Tổng khấu trừ
    private BigDecimal netSalary;       // Lương thực nhận
    private List<PayrollLineDTO> lines; // Chi tiết từng khoản

    public static class PayrollLineDTO {
        private String code;
        private String name;
        private String direction;
        private BigDecimal amount;
        private String explanation;

        public PayrollLineDTO(String code, String name, String direction, BigDecimal amount, String explanation) {
            this.code = code;
            this.name = name;
            this.direction = direction;
            this.amount = amount;
            this.explanation = explanation;
        }

        public String getCode() { return code; }
        public String getName() { return name; }
        public String getDirection() { return direction; }
        public BigDecimal getAmount() { return amount; }
        public String getExplanation() { return explanation; }
    }

    public Long getPayrollDetailId() { return payrollDetailId; }
    public void setPayrollDetailId(Long payrollDetailId) { this.payrollDetailId = payrollDetailId; }
    public Long getEmployeeId() { return employeeId; }
    public void setEmployeeId(Long employeeId) { this.employeeId = employeeId; }
    public BigDecimal getTotalGross() { return totalGross; }
    public void setTotalGross(BigDecimal totalGross) { this.totalGross = totalGross; }
    public BigDecimal getTotalDeduction() { return totalDeduction; }
    public void setTotalDeduction(BigDecimal totalDeduction) { this.totalDeduction = totalDeduction; }
    public BigDecimal getNetSalary() { return netSalary; }
    public void setNetSalary(BigDecimal netSalary) { this.netSalary = netSalary; }
    public List<PayrollLineDTO> getLines() { return lines; }
    public void setLines(List<PayrollLineDTO> lines) { this.lines = lines; }
}