package com.hrm.service;

import com.hrm.dto.ScheduleChangeDTOs.BreakWindow;
import com.hrm.dto.ScheduleChangeDTOs.Proposal;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

/**
 * Pure input/time validation. Authorization, persistence, overlap checks and
 * audit remain server-side responsibilities.
 */
@Component
public class ScheduleChangePolicy {
    private static final int MAX_REASON_LENGTH = 1000;

    public void validateProposal(Proposal proposal, LocalDateTime originalStart, Clock clock) {
        if (proposal == null || proposal.scheduleId() <= 0 || originalStart == null || clock == null)
            bad("Missing schedule change input");
        LocalDateTime now = LocalDateTime.now(clock);
        if (!now.isBefore(originalStart))
            conflict("Original schedule has started; changes are not allowed");
        if (proposal.proposedStart() == null || proposal.proposedEnd() == null
                || !proposal.proposedStart().isBefore(proposal.proposedEnd()))
            bad("Invalid proposed time range");
        if (!now.isBefore(proposal.proposedStart()))
            conflict("Proposed schedule has already started");
        if (proposal.proposedShiftId() != null && proposal.proposedShiftId() <= 0)
            bad("Invalid shift ID");
        if (proposal.reason() == null || proposal.reason().isBlank()
                || proposal.reason().length() > MAX_REASON_LENGTH)
            bad("A reason of up to 1000 characters is required");
        List<BreakWindow> breaks = proposal.proposedBreaks();
        if (breaks == null)
            bad("Break details are required, even when empty");
        if (breaks.stream().anyMatch(Objects::isNull))
            bad("Null break window");
        List<BreakWindow> sorted = breaks.stream().sorted(Comparator.comparing(BreakWindow::start,
                Comparator.nullsFirst(Comparator.naturalOrder()))).toList();
        LocalDateTime last = null;
        for (BreakWindow item : sorted) {
            if (item.start() == null || item.end() == null || !item.start().isBefore(item.end())
                    || item.start().isBefore(proposal.proposedStart())
                    || item.end().isAfter(proposal.proposedEnd()))
                bad("Break must be within proposed schedule");
            if (last != null && item.start().isBefore(last))
                bad("Overlapping breaks");
            if (Duration.between(item.start(), item.end()).toMinutes() <= 0)
                bad("Break must last at least one minute");
            last = item.end();
        }
    }

    private static void bad(String reason) {
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, reason);
    }

    private static void conflict(String reason) {
        throw new ResponseStatusException(HttpStatus.CONFLICT, reason);
    }
}
