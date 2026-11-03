package com.hrm.service;

import java.time.*;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/** No processing deadline is fabricated before the official working calendar is configured. */
@Service
public class AttendancePortalDeadlinePolicy {
    private final boolean configured;
    private final Set<LocalDate> holidays;
    private final Set<DayOfWeek> weekends;
    public AttendancePortalDeadlinePolicy(@Value("${hrm.attendance.calendar-configured:false}") boolean configured,
            @Value("${hrm.attendance.calendar-holidays:}") String holidays,
            @Value("${hrm.attendance.calendar-weekends:SATURDAY,SUNDAY}") String weekends){
        this.configured=configured;this.holidays=new HashSet<>();this.weekends=new HashSet<>();
        for(String value:holidays.split(","))if(!value.isBlank())this.holidays.add(LocalDate.parse(value.trim()));
        for(String value:weekends.split(","))if(!value.isBlank())this.weekends.add(DayOfWeek.valueOf(value.trim()));
        if(this.weekends.size()==7)throw new IllegalArgumentException("Working calendar must have working days");
    }
    public LocalDateTime processingDeadline(LocalDate received){
        if(!configured)return null;
        return new AttendanceRequestDeadlineService().processingDeadline(received,date->!weekends.contains(date.getDayOfWeek())&&!holidays.contains(date)).withNano(999_000_000);
    }
}
