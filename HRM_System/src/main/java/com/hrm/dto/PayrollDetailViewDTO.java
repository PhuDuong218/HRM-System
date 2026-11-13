package com.hrm.dto;

import java.math.BigDecimal;
import java.util.List;

public class PayrollDetailViewDTO {
    private Long payrollDetailId;
    private Long employeeId;
    private String employeeName;
    private String employeeCode;
    private String departmentName;
    private BigDecimal baseSalary;
    private BigDecimal workDays;
    private Long payslipId;
    private boolean missingSalary;
    private boolean provisional;
    private BigDecimal totalGross;      // Tổng thu nhập
    private BigDecimal totalDeduction;  // Tổng khấu trừ
    private BigDecimal netSalary;       // Lương thực nhận
    private List<PayrollLineDTO> lines; // Chi tiết từng khoản

    public boolean isProvisional() { return provisional; }
    public void setProvisional(boolean provisional) { this.provisional = provisional; }

    public String getEmployeeCode() { return employeeCode; }
    public void setEmployeeCode(String employeeCode) { this.employeeCode = employeeCode; }

    public String getDepartmentName() { return departmentName; }
    public void setDepartmentName(String departmentName) { this.departmentName = departmentName; }

    public BigDecimal getBaseSalary() { return baseSalary; }
    public void setBaseSalary(BigDecimal baseSalary) { this.baseSalary = baseSalary; }

    public BigDecimal getWorkDays() { return workDays; }
    public void setWorkDays(BigDecimal workDays) { this.workDays = workDays; }

    public Long getPayslipId() { return payslipId; }
    public void setPayslipId(Long payslipId) { this.payslipId = payslipId; }

    public boolean isMissingSalary() { return missingSalary; }
    public void setMissingSalary(boolean missingSalary) { this.missingSalary = missingSalary; }

    public static class PayrollLineDTO {
        private String code;
        private String name;
        private String direction;
        private BigDecimal amount;
        private String explanation;
        private String deductionShareText;

        public PayrollLineDTO(String code, String name, String direction, BigDecimal amount, String explanation) {
            this.code = code;
            this.name = name;
            this.direction = direction;
            this.amount = amount;
            this.explanation = displayExplanation(explanation);
        }

        private static String displayExplanation(String explanation) {
            if (explanation == null) return null;
            return explanation.replaceAll("(?iu)hệ số nháp\\s+", "hệ số nhân ×")
                .replace("Bảo hiểm tạm tính theo chính sách đồ án",
                         "Mức đóng tham chiếu theo quy định: BHXH 8% · BHYT 1,5% · BHTN 1% trên căn cứ đóng tương ứng. Khoản trừ theo phiếu đã lưu.")
                .replace("Thuế tạm tính theo chính sách đồ án",
                         "Thuế TNCN đã khấu trừ theo phiếu đã lưu. Tỷ trọng trên tổng thu nhập khác với thuế suất.");
        }

        public void describeDeductionShare(BigDecimal gross) {
            if (!"DEDUCT".equalsIgnoreCase(direction) || amount == null || gross == null || gross.signum() <= 0) return;
            String percent = amount.movePointRight(2).divide(gross, 2, java.math.RoundingMode.HALF_UP)
                .stripTrailingZeros().toPlainString().replace('.', ',');
            deductionShareText = percent + "% tổng thu nhập";
        }

        public String getCode() { return code; }
        public String getName() { return name; }
        public String getDirection() { return direction; }
        public BigDecimal getAmount() { return amount; }
        public String getDeductionShareText() { return deductionShareText; }
        public String getExplanation() { return explanation; }
    }

    public String getEmployeeName() { return employeeName; }
    public void setEmployeeName(String employeeName) { this.employeeName = employeeName; }
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
