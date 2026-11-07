package com.hrm;

import com.hrm.service.OvertimePolicy;

import static org.junit.jupiter.api.Assertions.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

class OvertimePolicyTest {
    @Test
    void deadlineThreeCalendarDays() {
        assertTrue(OvertimePolicy.withinSubmissionWindow(LocalDate.of(2026, 10, 10), LocalDate.of(2026, 10, 13)));
        assertFalse(OvertimePolicy.withinSubmissionWindow(LocalDate.of(2026, 10, 10), LocalDate.of(2026, 10, 14)));
    }

    @Test
    void overlapHalfOpen() {
        var a = LocalDateTime.of(2026, 10, 10, 18, 0);
        assertTrue(OvertimePolicy.overlaps(a, a.plusHours(2), a.plusHours(1), a.plusHours(3)));
        assertFalse(OvertimePolicy.overlaps(a, a.plusHours(2), a.plusHours(2), a.plusHours(3)));
    }

    @Test
    void netBreakAndLimit() {
        var start = LocalDateTime.of(2026, 10, 10, 18, 0);
        assertEquals(90, OvertimePolicy.netMinutes(start, start.plusHours(2), 30));
        assertThrows(IllegalArgumentException.class, () -> OvertimePolicy.netMinutes(start, start.plusHours(2), 121));
        OvertimePolicy.requireDailyLimit(480, 240);
        assertThrows(IllegalArgumentException.class, () -> OvertimePolicy.requireDailyLimit(480, 241));
    }

    @Test
    void validateInputs() {
        var t = LocalDateTime.of(2026, 10, 10, 18, 0);
        assertThrows(IllegalArgumentException.class, () -> OvertimePolicy.validateRequest(t, t, "reason"));
        assertThrows(IllegalArgumentException.class, () -> OvertimePolicy.validateRequest(t, t.plusHours(1), "  "));
        assertTrue(OvertimePolicy.actualWithinApprovedInterval(t, t.plusHours(3), t.plusMinutes(15), t.plusHours(2)));
        assertFalse(OvertimePolicy.actualWithinApprovedInterval(t, t.plusHours(3), t.minusMinutes(1), t.plusHours(2)));
    }
}
