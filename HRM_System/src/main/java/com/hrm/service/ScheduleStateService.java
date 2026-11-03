package com.hrm.service;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

/**
 * Read-only, event-derived state for existing work_schedules (no schema
 * changes).
 */
@Service
public class ScheduleStateService {
    public enum State {
        ACTIVE, EDIT_LOCKED, CANCELLED
    }

    private final JdbcTemplate db;

    public ScheduleStateService(JdbcTemplate db) {
        this.db = db;
    }

    public State current(long scheduleId) {
        if (scheduleId <= 0)
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy lịch");
        Long cancelled=db.queryForObject("SELECT COUNT_BIG(*) FROM dbo.work_schedules WHERE schedule_id=? AND cancelled_at IS NOT NULL",Long.class,scheduleId);
        if(cancelled!=null && cancelled>0)return State.CANCELLED;
        var rows = db.query("""
                SELECT TOP (1) action FROM dbo.audit_logs
                WHERE target_table='work_schedules' AND target_record_key=?
                  AND action IN ('WORK_SCHEDULE_EDIT_LOCKED',
                                 'WORK_SCHEDULE_EDIT_UNLOCKED', 'WORK_SCHEDULE_CANCELLED')
                ORDER BY occurred_at DESC, audit_id DESC
                """, (rs, index) -> rs.getString(1), Long.toString(scheduleId));
        if (rows.isEmpty())
            return State.ACTIVE;
        return switch (rows.get(0)) {
            case "WORK_SCHEDULE_EDIT_LOCKED" -> State.EDIT_LOCKED;
            case "WORK_SCHEDULE_CANCELLED" -> State.CANCELLED;
            default -> State.ACTIVE;
        };
    }

    /** Call inside the same scheduleTransactionManager transaction as the write. */
    public void requireEffective(long scheduleId) {
        if (current(scheduleId) == State.CANCELLED)
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Lịch đã hủy hiệu lực");
    }

    public void requireEditable(long scheduleId) {
        State state = current(scheduleId);
        if (state != State.ACTIVE)
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    state == State.CANCELLED ? "Lịch đã hủy hiệu lực" : "Lịch đã khóa chỉnh sửa");
    }
}
