
package com.hrm.repository.projection;

import java.math.BigDecimal;

public interface WorkStatisticsRow {

    Long getConfirmedWorkDays();

    Long getConfirmedAbsentDays();

    Long getApprovedLeaveRequests();

    BigDecimal getApprovedLeaveHours();

    Long getApprovedOvertimeRequests();

    BigDecimal getApprovedOvertimeHours();
}
