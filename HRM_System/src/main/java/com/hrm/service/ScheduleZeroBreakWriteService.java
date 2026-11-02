package com.hrm.service;

import com.hrm.dto.WorkTimeDTOs.ScheduleInput;
import com.hrm.dto.WorkTimeDTOs.ScheduleView;
import com.hrm.security.AuthenticatedAccount;
import java.sql.Timestamp;
import java.time.*;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.json.JsonMapper;

/**
 * Write only 0-break schedules using existing tables, without enabling legacy
 * task APIs.
 */
@Service
public class ScheduleZeroBreakWriteService {
    private final WorkScheduleService schedules;
    private final JdbcTemplate db;
    private final JsonMapper json;

    public ScheduleZeroBreakWriteService(WorkScheduleService schedules, JdbcTemplate db, JsonMapper json) {
        this.schedules = schedules;
        this.db = db;
        this.json = json;
    }

    public boolean canManage(AuthenticatedAccount actor) {
        if (actor == null || actor.accountId() == null || actor.roleCode() == null)
            return false;
        Long found = db.queryForObject("""
                SELECT COUNT_BIG(*) FROM dbo.accounts a JOIN dbo.roles r ON r.role_id=a.role_id
                WHERE a.account_id=? AND a.is_active=1 AND r.role_code=? AND r.role_code IN ('ADMIN','HR')
                """, Long.class, actor.accountId(), actor.roleCode());
        return found != null && found == 1L;
    }

    private static ResponseStatusException invalid(String message) {
        return new ResponseStatusException(HttpStatus.CONFLICT, message);
    }

    private void checkInput(ScheduleInput input) {
        if (input == null || input.employeeId() == null || input.employeeId() <= 0 || input.shiftId() == null
                || input.shiftId() <= 0
                || input.plannedStart() == null || input.plannedEnd() == null
                || !input.plannedEnd().isAfter(input.plannedStart())
                || input.plannedBreakMinutes() != 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Chỉ hỗ trợ lịch có ca mẫu và 0 phút nghỉ");
        }
        if (input.note() != null && input.note().length() > 1000)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Ghi chú quá 1000 ký tự");
        if (Duration.between(input.plannedStart(), input.plannedEnd()).toMinutes() > 1440)
            throw invalid("Ca không được vượt 24 giờ");
        // Do not silently convert a 30/60-minute shift to an unverified 0-break
        // schedule.
        var template = db.query("""
                SELECT start_time,end_time,end_day_offset,break_minutes,is_active
                FROM dbo.work_shifts WITH(HOLDLOCK) WHERE shift_id=?
                """, (rs, n) -> new Object[] { rs.getTime(1).toLocalTime(), rs.getTime(2).toLocalTime(),
                rs.getInt(3), rs.getInt(4), rs.getBoolean(5) }, input.shiftId());
        if (template.size() != 1 || !((Boolean) template.get(0)[4]))
            throw invalid("Mẫu ca không tồn tại hoặc đã khóa");
        Object[] t = template.get(0);
        if ((Integer) t[3] != 0)
            throw invalid("Mẫu ca có khoảng nghỉ chưa xác minh; không được dùng luồng 0 phút nghỉ");
        LocalDateTime expectedEnd = input.plannedStart().toLocalDate().atTime((LocalTime) t[1])
                .plusDays((Integer) t[2]);
        if (!input.plannedStart().toLocalTime().equals(t[0]) || !input.plannedEnd().equals(expectedEnd))
            throw invalid("Giờ lịch phải trùng giờ của mẫu ca đã chọn");
    }

    /**
     * Must run AFTER schedule employee applock is obtained by WorkScheduleService.
     */
    private void enforceDailyLimit(long employeeId, LocalDateTime start, LocalDateTime end) {
        LocalDate day = start.toLocalDate();
        LocalDate last = end.minusNanos(1).toLocalDate();
        LocalDateTime windowStart = day.atStartOfDay();
        LocalDateTime windowEnd = last.plusDays(1).atStartOfDay();
        var rows = db.query("""
                SELECT planned_start,planned_end,planned_break_minutes FROM dbo.work_schedules WITH(HOLDLOCK)
                WHERE cancelled_at IS NULL AND employee_id=? AND planned_start<? AND planned_end>?
                """,
                (rs, n) -> new Object[] { rs.getTimestamp(1).toLocalDateTime(), rs.getTimestamp(2).toLocalDateTime(),
                        rs.getInt(3) },
                employeeId, Timestamp.valueOf(windowEnd), Timestamp.valueOf(windowStart));
        for (Object[] row : rows) {
            if ((Integer) row[2] != 0)
                throw invalid(
                        "Có lịch cũ với phút nghỉ chưa đối soát trong khoảng kiểm tra; không thể xác minh giới hạn 12 giờ");
        }
        for (LocalDate d = day; !d.isAfter(last); d = d.plusDays(1)) {
            LocalDateTime d0 = d.atStartOfDay(), d1 = d.plusDays(1).atStartOfDay();
            long total = 0;
            for (Object[] row : rows) {
                LocalDateTime a = (LocalDateTime) row[0], b = (LocalDateTime) row[1];
                LocalDateTime left = a.isAfter(d0) ? a : d0, right = b.isBefore(d1) ? b : d1;
                if (right.isAfter(left))
                    total += Duration.between(left, right).toMinutes();
            }
            if (total > 720)
                throw invalid("Tổng giờ làm theo ngày vượt 12 giờ: " + d);
        }
    }

    private void audit(AuthenticatedAccount actor, long id, String action, String reason, Object before, Object after) {
        db.update(
                """
                        INSERT INTO dbo.audit_logs(actor_account_id,occurred_at,action,target_table,target_record_key,reason,before_data_json,after_data_json)
                        VALUES (?,SYSUTCDATETIME(),?,'work_schedules',?,?,?,?)
                        """,
                actor.accountId(), action, String.valueOf(id), reason,
                before == null ? null : json.writeValueAsString(before),
                after == null ? null : json.writeValueAsString(after));
    }

    @Transactional("scheduleTransactionManager")
    public ScheduleView create(AuthenticatedAccount actor, ScheduleInput input) {
        checkInput(input);
        // Internal method acquires the employee transaction-scoped applock and checks
        // RBAC/overlap.
        ScheduleView created = schedules.createWithVerifiedBreaks(actor, input);
        enforceDailyLimit(input.employeeId(), input.plannedStart(), input.plannedEnd());
        audit(actor, created.scheduleId(), "WORK_SCHEDULE_CREATE", "Phân công lịch không nghỉ", null, created);
        return created;
    }

    @Transactional("scheduleTransactionManager")
    public ScheduleView update(AuthenticatedAccount actor, long id, ScheduleInput input) {
        checkInput(input);
        ScheduleView before = schedules.get(id);
        if (before.plannedBreakMinutes() != 0)
            throw invalid("Không sửa lịch cũ có phút nghỉ chưa xác minh");
        // Do not change schedules that already have attendance, including
        // historical/confirmed work.
        Long attendanceCount = db.queryForObject(
                "SELECT COUNT_BIG(*) FROM dbo.attendance_records WITH(HOLDLOCK) WHERE schedule_id=?", Long.class, id);
        if (attendanceCount != null && attendanceCount > 0)
            throw invalid("Lịch đã có chấm công, không được sửa qua luồng thường");
        ScheduleView updated = schedules.updateWithVerifiedBreaks(actor, id, input);
        enforceDailyLimit(input.employeeId(), input.plannedStart(), input.plannedEnd());
        audit(actor, id, "WORK_SCHEDULE_UPDATE", "Điều chỉnh lịch không nghỉ", before, updated);
        return updated;
    }
}
