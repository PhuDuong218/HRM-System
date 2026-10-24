package com.hrm.service.provider;

import com.hrm.dto.AttendanceSummaryDTO;
import java.time.LocalDate;

/**
 * P-07: Interface cung cấp số liệu chấm công, tăng ca và nghỉ phép
 */
public interface AttendanceDataProvider {

    /**
     * Lấy tổng hợp ngày công, giờ OT và ngày nghỉ của một nhân viên trong kỳ
     */
    AttendanceSummaryDTO getAttendanceSummary(Long employeeId, LocalDate periodStart, LocalDate periodEnd);
}