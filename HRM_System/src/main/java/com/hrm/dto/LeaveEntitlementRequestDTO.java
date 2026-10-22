
package com.hrm.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public class LeaveEntitlementRequestDTO {

    @NotNull(message = "ID nhân viên không được để trống")
    @Positive(message = "ID nhân viên phải lớn hơn 0")
    private Long employeeId;

    @NotNull(message = "ID loại nghỉ không được để trống")
    @Positive(message = "ID loại nghỉ phải lớn hơn 0")
    private Long leaveTypeId;

    @NotNull(message = "Ngày bắt đầu kỳ không được để trống")
    private LocalDate periodStart;

    @NotNull(message = "Ngày kết thúc kỳ không được để trống")
    private LocalDate periodEnd;

    @NotNull(message = "Số giờ nghỉ được cấp không được để trống")
    @DecimalMin(value = "0.0", inclusive = false,
            message = "Số giờ nghỉ phải lớn hơn 0")
    @Digits(integer = 5, fraction = 4,
            message = "Số giờ không đúng định dạng decimal(9,4)")
    private BigDecimal entitledHours;

    public Long getEmployeeId() {
        return employeeId;
    }

    public void setEmployeeId(Long employeeId) {
        this.employeeId = employeeId;
    }

    public Long getLeaveTypeId() {
        return leaveTypeId;
    }

    public void setLeaveTypeId(Long leaveTypeId) {
        this.leaveTypeId = leaveTypeId;
    }

    public LocalDate getPeriodStart() {
        return periodStart;
    }

    public void setPeriodStart(LocalDate periodStart) {
        this.periodStart = periodStart;
    }

    public LocalDate getPeriodEnd() {
        return periodEnd;
    }

    public void setPeriodEnd(LocalDate periodEnd) {
        this.periodEnd = periodEnd;
    }

    public BigDecimal getEntitledHours() {
        return entitledHours;
    }

    public void setEntitledHours(BigDecimal entitledHours) {
        this.entitledHours = entitledHours;
    }
}
