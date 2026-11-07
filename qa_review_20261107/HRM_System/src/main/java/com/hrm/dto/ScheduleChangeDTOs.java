package com.hrm.dto;

import java.time.LocalDateTime;
import java.util.List;

/**
 * DTOs for a proposed replacement schedule. No database write is performed by
 * these records.
 */
public final class ScheduleChangeDTOs {
    private ScheduleChangeDTOs() {
    }

    public record BreakWindow(LocalDateTime start, LocalDateTime end) {
    }

    public record Proposal(
            long scheduleId,
            Long proposedShiftId,
            LocalDateTime proposedStart,
            LocalDateTime proposedEnd,
            List<BreakWindow> proposedBreaks,
            String reason) {
    }
}
