package com.hrm.service.provider.impl;

import com.hrm.dto.AttendanceSummaryDTO;
import com.hrm.service.provider.AttendanceDataProvider;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;

@Service
public class MockAttendanceDataProvider implements AttendanceDataProvider {

    @Override
    public AttendanceSummaryDTO getAttendanceSummary(Long employeeId, LocalDate periodStart, LocalDate periodEnd) {
        // Dữ liệu mẫu giả lập:
        // Công chuẩn: 22 ngày, Đi làm: 21 ngày, Nghỉ phép có lương: 1 ngày (tổng đủ 22 công), OT: 4.5 giờ
        return new AttendanceSummaryDTO(
                employeeId,
                new BigDecimal("22"),
                new BigDecimal("21"),
                new BigDecimal("4.5"),
                new BigDecimal("1"),
                new BigDecimal("0")
        );
    }
}