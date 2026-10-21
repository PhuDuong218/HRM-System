
package com.hrm.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public final class AttendanceDTOs {

    private AttendanceDTOs() {
    }

    // HR / Quản lý nhập chấm công thủ công
    public record AttendanceInput(
        @NotNull Long employeeId,
        Long scheduleId,
        @NotNull LocalDate workDate,
        LocalDateTime actualStart,
        LocalDateTime actualEnd,
        @Min(0) int actualBreakMinutes,
        @NotBlank String attendanceResult,
        String correctionReason
    ) {
    }

    // HR / Quản lý điều chỉnh chấm công
    public record AttendanceCorrection(
        LocalDateTime actualStart,
        LocalDateTime actualEnd,
        @Min(0) int actualBreakMinutes,
        @NotBlank String attendanceResult,
        @NotBlank String correctionReason
    ) {
    }

    // Kết quả trả về khi xem bản ghi chấm công
    public record AttendanceView(
        long attendanceId,
        long employeeId,
        Long scheduleId,
        LocalDate workDate,
        LocalDateTime actualStart,
        LocalDateTime actualEnd,
        int actualBreakMinutes,
        String attendanceResult,
        String verificationStatus,
        Long confirmedBy,
        LocalDateTime confirmedAt,
        String correctionReason
    ) {
    }
}
