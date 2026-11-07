package com.hrm.service;

import com.hrm.dto.DetailedWorkTimeDTOs.ShiftWithBreaks;
import com.hrm.dto.WorkTimeDTOs.ShiftInput;
import com.hrm.dto.WorkTimeDTOs.ShiftView;
import com.hrm.security.AuthenticatedAccount;
import java.time.LocalDateTime;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.json.JsonMapper;

/** Detailed shift CRUD without enabling unrelated hrm.task APIs. */
@Service
public class WorkShiftManagementWriteService {
    private final JdbcTemplate db;
    private final WorkShiftPermissionService permissions;
    private final WorkShiftService shifts;
    private final DetailedWorkTimeService detailed;
    private final JsonMapper json;

    public WorkShiftManagementWriteService(JdbcTemplate db, WorkShiftPermissionService permissions,
            WorkShiftService shifts, DetailedWorkTimeService detailed, JsonMapper json) {
        this.db = db;
        this.permissions = permissions;
        this.shifts = shifts;
        this.detailed = detailed;
        this.json = json;
    }

    private static ResponseStatusException conflict(String reason) {
        return new ResponseStatusException(HttpStatus.CONFLICT, reason);
    }

    private ShiftView locked(long id) {
        return db.query("""
                SELECT shift_id,shift_code,shift_name,start_time,end_time,end_day_offset,break_minutes,is_active
                FROM dbo.work_shifts WITH (UPDLOCK,HOLDLOCK) WHERE shift_id=?
                """, (r, n) -> new ShiftView(r.getLong(1), r.getString(2), r.getString(3),
                r.getTime(4).toLocalTime(), r.getTime(5).toLocalTime(), r.getInt(6), r.getInt(7), r.getBoolean(8)), id)
                .stream().findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy ca"));
    }

    private void audit(AuthenticatedAccount actor, long id, String action, String reason, Object before, Object after) {
        db.update("""
                INSERT INTO dbo.audit_logs(actor_account_id,occurred_at,action,target_table,
                    target_record_key,reason,before_data_json,after_data_json)
                VALUES (?,SYSUTCDATETIME(),?,'work_shifts',?,?,?,?)
                """, actor.accountId(), action, String.valueOf(id), reason,
                before == null ? null : json.writeValueAsString(before),
                after == null ? null : json.writeValueAsString(after));
    }

    @Transactional("scheduleTransactionManager")
    public ShiftView create(AuthenticatedAccount actor, ShiftWithBreaks input) {
        permissions.requireManage(actor);
        if (input == null || input.shift() == null || input.breaks() == null)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Thiếu thông tin mẫu ca");
        ShiftInput shift = input.shift();
        if (!shift.active())
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Ca mới phải ở trạng thái hoạt động");
        if (shift.shiftCode() == null || shift.shiftCode().isBlank()
                || shift.shiftCode().trim().length() > 50
                || !shift.shiftCode().trim().matches("[A-Za-z0-9_-]+"))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Mã ca không hợp lệ");
        if (shift.shiftName() == null || shift.shiftName().isBlank()
                || shift.shiftName().trim().length() > 200)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Tên ca không hợp lệ");
        ShiftView result = detailed.createShift(actor, input);
        audit(actor, result.shiftId(), "WORK_SHIFT_CREATE", "Tạo mẫu ca và khoảng nghỉ chi tiết", null, result);
        return result;
    }

    /** Only the name can change while detailed break storage is unavailable. */
    @Transactional("scheduleTransactionManager")
    public ShiftView updateBasicInfo(AuthenticatedAccount actor, long id, String shiftName) {
        permissions.requireManage(actor);
        if (shiftName == null || shiftName.isBlank() || shiftName.trim().length() > 200)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Tên ca không hợp lệ");
        ShiftView before = locked(id);
        if (!before.active())
            throw conflict("Phải mở khóa ca trước khi sửa");
        String name = shiftName.trim();
        if (before.shiftName().equals(name))
            return before;
        int changed = db.update("UPDATE dbo.work_shifts SET shift_name=? WHERE shift_id=? AND is_active=1", name, id);
        if (changed != 1)
            throw conflict("Trạng thái ca đã thay đổi");
        ShiftView after = locked(id);
        audit(actor, id, "WORK_SHIFT_BASIC_INFO_UPDATE", "Đổi tên mẫu ca", before, after);
        return after;
    }

    /** Preserve existing schedules; update only the template and its break history. */
    @Transactional("scheduleTransactionManager")
    public ShiftView update(AuthenticatedAccount actor, long id, ShiftWithBreaks input) {
        permissions.requireManage(actor);
        if (input == null || input.shift() == null || input.breaks() == null)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Thiếu thông tin mẫu ca");
        ShiftView before = locked(id);
        if (!before.shiftCode().equals(input.shift().shiftCode()))
            throw conflict("Không được thay đổi mã ca");
        ShiftView after = detailed.updateShift(actor, id, input);
        audit(actor, id, "WORK_SHIFT_UPDATE", "Cập nhật mẫu ca và khoảng nghỉ chi tiết", before, after);
        return after;
    }

    @Transactional("scheduleTransactionManager")
    public ShiftView setActive(AuthenticatedAccount actor, long id, boolean active, String reason) {
        permissions.requireManage(actor);
        if (reason == null || reason.isBlank())
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Bắt buộc nhập lý do khóa/mở khóa");
        ShiftView before = locked(id);
        if (before.active() == active)
            throw conflict("Ca đã ở trạng thái yêu cầu");
        if (active) {
            // Avoid activating a template with invalid duration or unverifiable break
            // details.
            long duration = java.time.Duration.between(before.startTime(), before.endTime()).toMinutes()
                    + 1440L * before.endDayOffset();
            if (duration <= 0 || duration > 1440 || before.breakMinutes() < 0 || before.breakMinutes() > duration)
                throw conflict("Thông tin ca không hợp lệ để mở khóa");
            // Re-activation restores the existing template only. No detailed-break
            // verification is possible in hrm_db (no work_shift_breaks table).
            // This check validates aggregate minutes, NOT break interval timing.
            // Do not mutate break_minutes, times, code, or schedules here.
        }
        int changed = db.update("UPDATE dbo.work_shifts SET is_active=? WHERE shift_id=? AND is_active=?",
                active, id, !active);
        if (changed != 1)
            throw conflict("Trạng thái ca đã thay đổi");
        ShiftView after = locked(id);
        audit(actor, id, active ? "WORK_SHIFT_ACTIVATE" : "WORK_SHIFT_DEACTIVATE", reason.trim(), before, after);
        return after;
    }
}
