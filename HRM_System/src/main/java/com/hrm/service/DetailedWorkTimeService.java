package com.hrm.service;

import com.hrm.dto.DetailedWorkTimeDTOs.*;
import com.hrm.dto.WorkTimeDTOs.*;
import com.hrm.security.AuthenticatedAccount;
import java.sql.Timestamp;
import java.time.*;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * Strict, additive write entry points for the new break-detail schema.
 * All writes occur in the existing scheduleTransactionManager transaction.
 * Historic LEGACY_UNVERIFIED schedules are never modified by these methods.
 */
@Service
public class DetailedWorkTimeService {
    private final JdbcTemplate db;
    private final WorkShiftService shifts;
    private final WorkScheduleService schedules;

    public DetailedWorkTimeService(JdbcTemplate db, WorkShiftService shifts, WorkScheduleService schedules) {
        this.db = db;
        this.shifts = shifts;
        this.schedules = schedules;
    }

    private static ResponseStatusException conflict(String reason) {
        return new ResponseStatusException(HttpStatus.CONFLICT, reason);
    }

    private static ResponseStatusException bad(String reason) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, reason);
    }

    private static List<OffsetBreak> offsetBreaks(List<OffsetBreak> value, ShiftInput shift) {
        if (value == null || shift == null || shift.startTime() == null || shift.endTime() == null)
            throw bad("Missing shift/break details");
        long duration = Duration.between(shift.startTime(), shift.endTime()).toMinutes()
                + (long) shift.endDayOffset() * 1440;
        if (duration <= 0 || duration > 1440)
            throw bad("Invalid shift duration");
        List<OffsetBreak> sorted = new ArrayList<>(value);
        if (sorted.stream().anyMatch(Objects::isNull))
            throw bad("Null shift break");
        sorted.sort(Comparator.comparingInt(OffsetBreak::startMinute));
        long total = 0;
        int end = 0;
        for (OffsetBreak b : sorted) {
            if (b.startMinute() < end || b.startMinute() < 0 || b.endMinute() <= b.startMinute()
                    || b.endMinute() > duration)
                throw bad("Invalid/overlapping shift break");
            total += b.endMinute() - b.startMinute();
            end = b.endMinute();
        }
        if (total != shift.breakMinutes())
            throw bad("breakMinutes must equal sum of shift breaks");
        return sorted;
    }

    private static List<TimedBreak> timedBreaks(List<TimedBreak> value, ScheduleInput schedule) {
        if (value == null || schedule == null || schedule.plannedStart() == null || schedule.plannedEnd() == null)
            throw bad("Missing schedule/break details");
        List<TimedBreak> sorted = new ArrayList<>(value);
        if (sorted.stream().anyMatch(b -> b == null || b.start() == null || b.end() == null))
            throw bad("Null schedule break");
        sorted.sort(Comparator.comparing(TimedBreak::start));
        LocalDateTime end = null;
        long total = 0;
        for (TimedBreak b : sorted) {
            if (b.start().isBefore(schedule.plannedStart()) || b.end().isAfter(schedule.plannedEnd())
                    || !b.start().isBefore(b.end()) || (end != null && b.start().isBefore(end)))
                throw bad("Invalid/overlapping schedule break");
            long seconds = Duration.between(b.start(), b.end()).getSeconds();
            if (b.start().getSecond() != 0 || b.end().getSecond() != 0 || b.start().getNano() != 0
                    || b.end().getNano() != 0
                    || seconds % 60 != 0)
                throw bad("Break boundaries must be whole minutes");
            total += seconds / 60;
            end = b.end();
        }
        if (total != schedule.plannedBreakMinutes())
            throw bad("plannedBreakMinutes must equal sum of detailed breaks");
        return sorted;
    }

    @Transactional("scheduleTransactionManager")
    public ShiftView createShift(AuthenticatedAccount actor, ShiftWithBreaks request) {
        if (request == null)
            throw bad("Missing body");
        List<OffsetBreak> breaks = offsetBreaks(request.breaks(), request.shift());
        ShiftView shift = shifts.createWithVerifiedBreaks(actor, request.shift());
        writeShiftBreaks(shift.shiftId(), breaks);
        return shift;
    }

    @Transactional("scheduleTransactionManager")
    public ShiftView updateShift(AuthenticatedAccount actor, long id, ShiftWithBreaks request) {
        if (request == null)
            throw bad("Missing body");
        List<OffsetBreak> breaks = offsetBreaks(request.breaks(), request.shift());
        // Preserve old schedules as snapshots. The original service also protects
        // changes to used shift time.
        ShiftView result = shifts.updateWithVerifiedBreaks(actor, id, request.shift());
        db.update(
                "UPDATE dbo.work_shift_breaks SET superseded_at=SYSUTCDATETIME() WHERE shift_id=? AND superseded_at IS NULL",
                id);
        writeShiftBreaks(id, breaks);
        return result;
    }

    private void writeShiftBreaks(long id, List<OffsetBreak> breaks) {
        for (OffsetBreak b : breaks) {
            db.update(
                    "INSERT dbo.work_shift_breaks(shift_id,break_start_offset_minutes,break_end_offset_minutes) VALUES(?,?,?)",
                    id, b.startMinute(), b.endMinute());
        }
    }

    @Transactional("scheduleTransactionManager")
    public ScheduleView createSchedule(AuthenticatedAccount actor, ScheduleWithBreaks request) {
        if (request == null)
            throw bad("Missing body");
        ScheduleInput schedule = request.schedule();
        List<TimedBreak> breaks = resolveBreaks(schedule, request.breaks());
        ScheduleView created = schedules.createWithVerifiedBreaks(actor, schedule);
        writeScheduleBreaks(created.scheduleId(), breaks);
        verifyDailyLimit(schedule.employeeId(), schedule.plannedStart(), schedule.plannedEnd());
        return created;
    }

    @Transactional("scheduleTransactionManager")
    public ScheduleView updateSchedule(AuthenticatedAccount actor, long id, ScheduleWithBreaks request) {
        if (request == null)
            throw bad("Missing body");
        ScheduleInput schedule = request.schedule();
        List<TimedBreak> breaks = resolveBreaks(schedule, request.breaks());
        String status = db.query(
                "SELECT break_detail_status FROM dbo.work_schedules WITH(UPDLOCK,HOLDLOCK) WHERE schedule_id=?",
                rs -> rs.next() ? rs.getString(1) : null, id);
        if (!"VERIFIED".equals(status))
            throw conflict("Legacy schedule cannot be silently converted to VERIFIED");
        ScheduleView previous = schedules.get(id);
        ScheduleView updated = schedules.updateWithVerifiedBreaks(actor, id, schedule);
        db.update(
                "UPDATE dbo.work_schedule_breaks SET superseded_at=SYSUTCDATETIME() WHERE schedule_id=? AND superseded_at IS NULL",
                id);
        writeScheduleBreaks(id, breaks);
        verifyDailyLimit(schedule.employeeId(), previous.plannedStart(), previous.plannedEnd());
        verifyDailyLimit(schedule.employeeId(), schedule.plannedStart(), schedule.plannedEnd());
        return updated;
    }

    private List<TimedBreak> resolveBreaks(ScheduleInput schedule, List<TimedBreak> supplied) {
        if (schedule == null)
            throw bad("Missing schedule");
        if (schedule.shiftId() == null)
            return timedBreaks(supplied, schedule);
        ShiftView shift = shifts.get(schedule.shiftId());
        if (!shift.active())
            throw conflict("Inactive shift");
        // For a schedule tied to a template, enforce the template snapshot instead of
        // accepting arbitrary breaks.
        long templateMinutes = Duration.between(shift.startTime(), shift.endTime()).toMinutes()
                + 1440L * shift.endDayOffset();
        long scheduleMinutes = Duration.between(schedule.plannedStart(), schedule.plannedEnd()).toMinutes();
        if (!shift.startTime().equals(schedule.plannedStart().toLocalTime()) || templateMinutes != scheduleMinutes)
            throw bad("Schedule duration/start differs from shift template");
        List<OffsetBreak> source = db.query(
                "SELECT break_start_offset_minutes,break_end_offset_minutes FROM dbo.work_shift_breaks WITH(HOLDLOCK) WHERE shift_id=? AND superseded_at IS NULL ORDER BY break_start_offset_minutes",
                (rs, i) -> new OffsetBreak(rs.getInt(1), rs.getInt(2)), schedule.shiftId());
        if (source.isEmpty() && shift.breakMinutes() > 0)
            throw conflict("Shift template has no verified detailed break data");
        List<TimedBreak> copied = source.stream()
                .map(b -> new TimedBreak(schedule.plannedStart().plusMinutes(b.startMinute()),
                        schedule.plannedStart().plusMinutes(b.endMinute())))
                .toList();
        if (supplied != null && !supplied.isEmpty() && !supplied.equals(copied))
            throw bad("Schedule breaks must match shift template");
        return timedBreaks(copied, schedule);
    }

    private void writeScheduleBreaks(long id, List<TimedBreak> breaks) {
        for (TimedBreak b : breaks) {
            db.update("INSERT dbo.work_schedule_breaks(schedule_id,break_start,break_end) VALUES(?,?,?)",
                    id, Timestamp.valueOf(b.start()), Timestamp.valueOf(b.end()));
        }
        db.update("UPDATE dbo.work_schedules SET break_detail_status='VERIFIED' WHERE schedule_id=?", id);
    }

    private void verifyDailyLimit(long employeeId, LocalDateTime start, LocalDateTime end) {
        LocalDateTime from = start.toLocalDate().atStartOfDay();
        LocalDateTime to = end.toLocalDate().plusDays(1).atStartOfDay();
        List<Object[]> rows = db.query("""
                SELECT schedule_id, planned_start, planned_end, break_detail_status
                FROM dbo.work_schedules WITH(HOLDLOCK)
                WHERE employee_id=? AND planned_start<? AND planned_end>?
                """, (rs, i) -> new Object[] { rs.getLong(1), rs.getTimestamp(2).toLocalDateTime(),
                rs.getTimestamp(3).toLocalDateTime(), rs.getString(4) }, employeeId, Timestamp.valueOf(to),
                Timestamp.valueOf(from));
        List<DailyWorkLimitPolicy.WorkSegment> segments = new ArrayList<>();
        for (Object[] row : rows) {
            long id = (Long) row[0];
            if (!"VERIFIED".equals(row[3]))
                throw conflict("Cannot verify daily hours: legacy schedule " + id + " lacks detailed breaks");
            List<DailyWorkLimitPolicy.Interval> breaks = db.query(
                    "SELECT break_start,break_end FROM dbo.work_schedule_breaks WITH(HOLDLOCK) WHERE schedule_id=? AND superseded_at IS NULL ORDER BY break_start",
                    (rs, i) -> new DailyWorkLimitPolicy.Interval(rs.getTimestamp(1).toLocalDateTime(),
                            rs.getTimestamp(2).toLocalDateTime()),
                    id);
            segments.add(new DailyWorkLimitPolicy.WorkSegment(new DailyWorkLimitPolicy.Interval(
                    (LocalDateTime) row[1], (LocalDateTime) row[2]), breaks));
        }
        try {
            DailyWorkLimitPolicy.requireWithinDailyLimit(segments);
        } catch (IllegalArgumentException ex) {
            throw bad(ex.getMessage());
        }
    }

    public List<TimedBreak> scheduleBreaks(long scheduleId) {
        return db.query(
                "SELECT break_start,break_end FROM dbo.work_schedule_breaks WHERE schedule_id=? AND superseded_at IS NULL ORDER BY break_start",
                (rs, i) -> new TimedBreak(rs.getTimestamp(1).toLocalDateTime(), rs.getTimestamp(2).toLocalDateTime()),
                scheduleId);
    }

    public List<OffsetBreak> shiftBreaks(long shiftId) {
        return db.query(
                "SELECT break_start_offset_minutes,break_end_offset_minutes FROM dbo.work_shift_breaks WHERE shift_id=? AND superseded_at IS NULL ORDER BY break_start_offset_minutes",
                (rs, i) -> new OffsetBreak(rs.getInt(1), rs.getInt(2)), shiftId);
    }
}
