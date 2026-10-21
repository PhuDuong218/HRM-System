package com.hrm.dto;

import java.time.*;
import jakarta.validation.constraints.*;

/** New request/response contracts, no changes to the existing entities. */
public final class WorkTimeDTOs {
 private WorkTimeDTOs() {}
 public record ShiftInput(@NotBlank String shiftCode, @NotBlank String shiftName,
   @NotNull LocalTime startTime, @NotNull LocalTime endTime,
   @Min(0) @Max(1) int endDayOffset, @Min(0) int breakMinutes, boolean active) {}
 public record ShiftView(long shiftId,String shiftCode,String shiftName,LocalTime startTime,
   LocalTime endTime,int endDayOffset,int breakMinutes,boolean active) {}
 public record ScheduleInput(@NotNull Long employeeId, Long shiftId, @NotNull LocalDateTime plannedStart,
   @NotNull LocalDateTime plannedEnd, @Min(0) int plannedBreakMinutes,String note) {}
 public record ScheduleView(long scheduleId,long employeeId,Long shiftId,LocalDate workDate,
   LocalDateTime plannedStart,LocalDateTime plannedEnd,int plannedBreakMinutes,String note) {}
 public record PayrollHours(long employeeId,long attendanceMinutes,long overtimeMinutes) {}
}
