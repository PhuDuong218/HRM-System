
package com.hrm.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record LeaveEntitlementDTO(
        Long entitlementId,
        Long employeeId,
        String employeeName,
        Long leaveTypeId,
        String leaveTypeName,
        LocalDate periodStart,
        LocalDate periodEnd,
        BigDecimal entitledHours
) {
}
