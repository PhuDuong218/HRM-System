package com.hrm.service;

import com.hrm.dto.WorkTimeDTOs.ScheduleView;
import com.hrm.security.AuthenticatedAccount;
import java.time.Clock;
import java.time.LocalDateTime;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/** Edit locking and pre-start cancellation are distinct audited operations. */
@Service
public class ScheduleStateWriteService {
    private final JdbcTemplate db;
    private final ScheduleStateService states;
    private final ScheduleWritePermissionService permissions;
    private final WorkScheduleService schedules;
    private final Clock clock;

    public ScheduleStateWriteService(JdbcTemplate db, ScheduleStateService states,
            ScheduleWritePermissionService permissions, WorkScheduleService schedules,
            @Qualifier("hrmBusinessClock") Clock clock) {
        this.db = db;
        this.states = states;
        this.permissions = permissions;
        this.schedules = schedules;
        this.clock = clock;
    }

    @Transactional("scheduleTransactionManager")
    public ScheduleStateService.State lockEdit(AuthenticatedAccount actor, long scheduleId, String reason) {
        return transition(actor, scheduleId, reason, true);
    }

    @Transactional("scheduleTransactionManager")
    public ScheduleStateService.State unlockEdit(AuthenticatedAccount actor, long scheduleId, String reason) {
        return transition(actor, scheduleId, reason, false);
    }

    private ScheduleStateService.State transition(AuthenticatedAccount actor, long id, String reason, boolean lock) {
        validateReason(reason);
        if (id <= 0)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "ID lịch không hợp lệ");
        // Serialize all state changes to this schedule by locking the real row first.
        var ids = db.query("SELECT schedule_id FROM dbo.work_schedules WITH (UPDLOCK,HOLDLOCK) WHERE schedule_id=?",
                (rs, n) -> rs.getLong(1), id);
        if (ids.isEmpty())
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy lịch");
        ScheduleView schedule = schedules.get(id);
        permissions.requireCreate(actor, schedule.employeeId(), schedule.plannedStart());
        var state = states.current(id);
        var expected = lock ? ScheduleStateService.State.ACTIVE : ScheduleStateService.State.EDIT_LOCKED;
        if (state != expected)
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Trạng thái lịch không phù hợp");
        String event = lock ? "WORK_SCHEDULE_EDIT_LOCKED" : "WORK_SCHEDULE_EDIT_UNLOCKED";
        db.update("""
                INSERT INTO dbo.audit_logs(actor_account_id,occurred_at,action,target_table,target_record_key,reason,
                                           before_data_json,after_data_json)
                VALUES (?,SYSUTCDATETIME(),?,'work_schedules',?,?,?,?)
                """, actor.accountId(), event, Long.toString(id), reason.trim(),
                "{\"state\":\"" + state + "\"}",
                "{\"state\":\"" + (lock ? "EDIT_LOCKED" : "ACTIVE") + "\"}");
        return states.current(id);
    }

    @Transactional("scheduleTransactionManager")
    public ScheduleStateService.State cancel(AuthenticatedAccount actor, long id, String reason) {
        validateReason(reason);
        if(id<=0)throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Lịch không hợp lệ");
        ScheduleView before=schedules.get(id);
        // Same employee row lock as OT; serialize cancellation with OT registration.
        db.query("SELECT employee_id FROM dbo.employees WITH(UPDLOCK,HOLDLOCK) WHERE employee_id=?",(rs,n)->rs.getLong(1),before.employeeId());
        var ids=db.query("SELECT schedule_id FROM dbo.work_schedules WITH (UPDLOCK,HOLDLOCK) WHERE schedule_id=?",(rs,n)->rs.getLong(1),id);
        if(ids.isEmpty())throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Không tìm thấy lịch");
        ScheduleView schedule=schedules.get(id);
        permissions.requireCreate(actor,schedule.employeeId(),schedule.plannedStart());
        var state=states.current(id);
        if(state==ScheduleStateService.State.CANCELLED)throw new ResponseStatusException(HttpStatus.CONFLICT,"Lịch đã được hủy");
        if(!LocalDateTime.now(clock).isBefore(schedule.plannedStart()))
            throw new ResponseStatusException(HttpStatus.CONFLICT,"Lịch đã bắt đầu, không được hủy lịch");
        requireNoDependency("SELECT COUNT_BIG(*) FROM dbo.attendance_records WITH(HOLDLOCK) WHERE schedule_id=?",
                "Lịch đã có chấm công; cần xử lý bản ghi công trước khi hủy",id);
        requireNoDependency("""
                SELECT COUNT_BIG(*) FROM dbo.schedule_change_request_details d WITH(HOLDLOCK)
                JOIN dbo.employee_requests r WITH(HOLDLOCK) ON r.request_id=d.request_id
                WHERE d.schedule_id=? AND r.status='CHO_DUYET'
                ""","Lịch đang có yêu cầu đổi lịch chờ duyệt; cần xử lý yêu cầu trước khi hủy",id);
        requireNoDependency("""
                SELECT COUNT_BIG(*) FROM dbo.employee_requests r WITH(HOLDLOCK)
                JOIN dbo.leave_request_details l WITH(HOLDLOCK) ON l.request_id=r.request_id
                WHERE r.employee_id=? AND r.status='DA_DUYET' AND l.leave_start<? AND l.leave_end>?
                ""","Lịch liên quan đến đơn nghỉ phép đã duyệt; cần đối soát trước khi hủy",schedule.employeeId(),schedule.plannedEnd(),schedule.plannedStart());
        requireNoDependency("""
                SELECT COUNT_BIG(*) FROM dbo.overtime_requests WITH(HOLDLOCK)
                WHERE employee_id=? AND status NOT IN ('TU_CHOI','DA_HUY') AND requested_start<? AND requested_end>?
                ""","Lịch liên quan đến tăng ca; cần đối soát trước khi hủy",schedule.employeeId(),schedule.plannedEnd(),schedule.plannedStart());
        // Recheck the deadline immediately before persistence, after dependency locks.
        LocalDateTime now=LocalDateTime.now(clock);
        if(!now.isBefore(schedule.plannedStart()))throw new ResponseStatusException(HttpStatus.CONFLICT,"Lịch đã bắt đầu, không được hủy lịch");
        int changed=db.update("""
                UPDATE dbo.work_schedules SET cancelled_at=SYSUTCDATETIME(),cancelled_by=?,cancellation_reason=?
                WHERE schedule_id=? AND cancelled_at IS NULL AND planned_start>?
                """,actor.accountId(),reason.trim(),id,now);
        if(changed!=1)throw new ResponseStatusException(HttpStatus.CONFLICT,"Lịch đã thay đổi hoặc không còn cho phép hủy");
        db.update("""
                INSERT INTO dbo.audit_logs(actor_account_id,occurred_at,action,target_table,target_record_key,reason,before_data_json,after_data_json)
                VALUES(?,SYSUTCDATETIME(),'WORK_SCHEDULE_CANCELLED','work_schedules',?,?,?,?)
                """,actor.accountId(),Long.toString(id),reason.trim(),"{\"state\":\""+state+"\"}","{\"state\":\"CANCELLED\"}");
        return ScheduleStateService.State.CANCELLED;
    }

    private void requireNoDependency(String sql,String message,Object... args){
        Long count=db.queryForObject(sql,Long.class,args);
        if(count==null || count>0)throw new ResponseStatusException(HttpStatus.CONFLICT,message);
    }

    private static void validateReason(String reason) {
        if (reason == null || reason.isBlank() || reason.trim().length() > 1000)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Lý do bắt buộc, tối đa 1000 ký tự");
    }
}
