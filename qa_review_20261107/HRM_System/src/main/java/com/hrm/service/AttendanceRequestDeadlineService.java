package com.hrm.service;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Objects;
import java.util.Set;
import org.springframework.stereotype.Service;

/**
 * Working-day deadline algorithm. Official HR holiday calendar must be supplied
 * by integration.
 */
@Service
public class AttendanceRequestDeadlineService {
    public interface WorkingCalendar {
        boolean isWorkingDay(LocalDate date);
    }

    public LocalDateTime processingDeadline(LocalDate receivedOn, WorkingCalendar calendar) {
        Objects.requireNonNull(receivedOn);
        Objects.requireNonNull(calendar);
        LocalDate day = receivedOn;
        int remaining = 2;
        while (remaining > 0) {
            day = day.plusDays(1); // receipt date excluded
            if (calendar.isWorkingDay(day))
                remaining--;
        }
        return day.atTime(LocalTime.MAX);
    }

    /**
     * Illustrative only; do not use to override a configured official HR calendar.
     */
    public static WorkingCalendar weekendsOnly() {
        return day -> day.getDayOfWeek() != DayOfWeek.SATURDAY
                && day.getDayOfWeek() != DayOfWeek.SUNDAY;
    }
}
