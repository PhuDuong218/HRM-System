package com.hrm.dto;

import java.math.BigDecimal;

public class AttendanceSummaryDTO {
    private Long employeeId;
    private BigDecimal standardWorkDays;  // Số ngày công chuẩn của tháng (ví dụ: 22 ngày)
    private BigDecimal actualWorkDays;    // Số ngày thực tế đi làm (ví dụ: 21 ngày)
    private BigDecimal overtimeHours;     // Số giờ tăng ca đã duyệt (ví dụ: 4 giờ)
    private BigDecimal paidLeaveDays;     // Số ngày nghỉ phép hưởng nguyên lương (ví dụ: 1 ngày)
    private BigDecimal unpaidLeaveDays;   // Số ngày nghỉ không lương (ví dụ: 0 ngày)

    public AttendanceSummaryDTO() {}

    public AttendanceSummaryDTO(Long employeeId, BigDecimal standardWorkDays, BigDecimal actualWorkDays,
                                BigDecimal overtimeHours, BigDecimal paidLeaveDays, BigDecimal unpaidLeaveDays) {
        this.employeeId = employeeId;
        this.standardWorkDays = standardWorkDays;
        this.actualWorkDays = actualWorkDays;
        this.overtimeHours = overtimeHours;
        this.paidLeaveDays = paidLeaveDays;
        this.unpaidLeaveDays = unpaidLeaveDays;
    }

    public Long getEmployeeId() { return employeeId; }
    public void setEmployeeId(Long employeeId) { this.employeeId = employeeId; }
    public BigDecimal getStandardWorkDays() { return standardWorkDays; }
    public void setStandardWorkDays(BigDecimal standardWorkDays) { this.standardWorkDays = standardWorkDays; }
    public BigDecimal getActualWorkDays() { return actualWorkDays; }
    public void setActualWorkDays(BigDecimal actualWorkDays) { this.actualWorkDays = actualWorkDays; }
    public BigDecimal getOvertimeHours() { return overtimeHours; }
    public void setOvertimeHours(BigDecimal overtimeHours) { this.overtimeHours = overtimeHours; }
    public BigDecimal getPaidLeaveDays() { return paidLeaveDays; }
    public void setPaidLeaveDays(BigDecimal paidLeaveDays) { this.paidLeaveDays = paidLeaveDays; }
    public BigDecimal getUnpaidLeaveDays() { return unpaidLeaveDays; }
    public void setUnpaidLeaveDays(BigDecimal unpaidLeaveDays) { this.unpaidLeaveDays = unpaidLeaveDays; }
}