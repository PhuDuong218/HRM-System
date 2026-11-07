package com.hrm.dto;

import java.time.LocalDateTime;
import java.util.List;
import com.hrm.dto.WorkTimeDTOs.ScheduleInput;
import com.hrm.dto.WorkTimeDTOs.ShiftInput;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

/** Additive contracts: keep existing WorkTimeDTOs binary/source compatible. */
public final class DetailedWorkTimeDTOs {
    private DetailedWorkTimeDTOs() {
    }

    public record OffsetBreak(int startMinute, int endMinute) {
    }

    public record TimedBreak(@NotNull LocalDateTime start, @NotNull LocalDateTime end) {
    }

    public record ShiftWithBreaks(@Valid @NotNull ShiftInput shift, @NotNull List<@Valid OffsetBreak> breaks) {
    }

    public record ScheduleWithBreaks(@Valid @NotNull ScheduleInput schedule, @NotNull List<@Valid TimedBreak> breaks) {
    }
}
