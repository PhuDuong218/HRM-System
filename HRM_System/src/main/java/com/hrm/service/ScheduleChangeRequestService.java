package com.hrm.service;

import com.hrm.dto.ScheduleChangeDTOs.Proposal;
import com.hrm.dto.ScheduleChangeDTOs.BreakWindow;
import com.hrm.dto.DetailedWorkTimeDTOs.ScheduleWithBreaks;
import com.hrm.dto.DetailedWorkTimeDTOs.TimedBreak;
import com.hrm.dto.WorkTimeDTOs.ScheduleInput;
import com.hrm.dto.WorkTimeDTOs.ScheduleView;
import com.hrm.security.AuthenticatedAccount;
import java.time.*;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * Draft workflow for review against approved migration. Never alters original
 * schedule on submission.
 */
@Service
public class ScheduleChangeRequestService {
    private final JdbcTemplate db;
    private final ScheduleChangePolicy policy;
    private final DetailedWorkTimeService detailed;
    private final ScheduleWritePermissionService writePermission;

    public ScheduleChangeRequestService(JdbcTemplate db, ScheduleChangePolicy policy,
            DetailedWorkTimeService detailed, ScheduleWritePermissionService writePermission) {
        this.db = db;
        this.policy = policy;
        this.detailed = detailed;
        this.writePermission = writePermission;
    }

    private static ResponseStatusException conflict(String text) {
        return new ResponseStatusException(HttpStatus.CONFLICT, text);
    }

    private static ResponseStatusException forbidden() {
        return new ResponseStatusException(HttpStatus.FORBIDDEN, "Schedule change permission denied");
    }

    private record LiveActor(long accountId, Long employeeId, String role) {
    }

    private LiveActor active(AuthenticatedAccount principal) {
        if (principal == null || principal.accountId() == null)
            throw forbidden();
        var actors = db.query("""
                SELECT a.account_id,a.employee_id,r.role_code FROM dbo.accounts a WITH(HOLDLOCK)
                JOIN dbo.roles r ON r.role_id=a.role_id WHERE a.account_id=? AND a.is_active=1
                """, (rs, i) -> new LiveActor(rs.getLong(1), rs.getObject(2, Long.class), rs.getString(3)),
                principal.accountId());
        if (actors.size() != 1)
            throw forbidden();
        LiveActor a = actors.get(0);
        if (!Objects.equals(a.employeeId(), principal.employeeId()) || !a.role().equals(principal.roleCode()))
            throw forbidden();
        return a;
    }

    @Transactional("scheduleTransactionManager")
    public long submit(AuthenticatedAccount principal, Proposal proposal) {
        LiveActor actor = active(principal);
        if (proposal == null || proposal.scheduleId() <= 0)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Missing proposal");
        if (actor.employeeId() == null || !(actor.role().equals("EMPLOYEE") || actor.role().equals("HR")))
            throw forbidden();
        // HR may propose a change for their OWN schedule, independent of HR department
        // scope.
        var owned = db.query("""
                SELECT schedule_id,employee_id,shift_id,work_date,planned_start,planned_end,planned_break_minutes,note
                FROM dbo.work_schedules WITH(UPDLOCK,HOLDLOCK)
                WHERE schedule_id=? AND employee_id=?
                """, (rs, i) -> new ScheduleView(rs.getLong(1), rs.getLong(2), rs.getObject(3, Long.class),
                rs.getDate(4).toLocalDate(), rs.getTimestamp(5).toLocalDateTime(),
                rs.getTimestamp(6).toLocalDateTime(), rs.getInt(7), rs.getString(8)),
                proposal.scheduleId(), actor.employeeId());
        if (owned.size() != 1)
            throw forbidden();
        ScheduleView original = owned.get(0);
        if (!"VERIFIED".equals(db.queryForObject("""
                SELECT break_detail_status FROM dbo.work_schedules WITH(UPDLOCK,HOLDLOCK) WHERE schedule_id=?
                """, String.class, proposal.scheduleId())))
            throw conflict("Legacy schedule needs break verification");
        policy.validateProposal(proposal, original.plannedStart(), Clock.system(ZoneId.of("Asia/Ho_Chi_Minh")));
        Long pending = db.queryForObject("""
                SELECT COUNT_BIG(*) FROM dbo.schedule_change_request_details d
                JOIN dbo.employee_requests r ON r.request_id=d.request_id
                WHERE d.schedule_id=? AND r.status='CHO_DUYET'
                """, Long.class, proposal.scheduleId());
        if (pending != null && pending > 0)
            throw conflict("Schedule change already pending");
        String code = "SCH-" + UUID.randomUUID().toString().replace("-", "").substring(0, 20);
        Long id = db.queryForObject("""
                INSERT dbo.employee_requests(employee_id,request_code,request_type,reason,created_by)
                OUTPUT INSERTED.request_id VALUES(?,?,'DOI_LICH',?,?)
                """, Long.class, actor.employeeId(), code, proposal.reason().trim(), actor.accountId());
        db.update(
                """
                        INSERT dbo.schedule_change_request_details
                        (request_id,schedule_id,original_start,original_end,proposed_shift_id,proposed_start,proposed_end,original_row_version)
                        SELECT ?,schedule_id,planned_start,planned_end,?,?,?,row_version
                        FROM dbo.work_schedules WHERE schedule_id=? AND employee_id=?
                        """,
                id, proposal.proposedShiftId(), proposal.proposedStart(), proposal.proposedEnd(),
                proposal.scheduleId(), actor.employeeId());
        for (BreakWindow b : proposal.proposedBreaks())
            db.update("INSERT dbo.schedule_change_request_breaks(request_id,break_start,break_end) VALUES(?,?,?)",
                    id, b.start(), b.end());
        audit(actor.accountId(), id, "SCHEDULE_CHANGE_SUBMIT", proposal.reason());
        return id;
    }

    /**
     * Atomic approval: invalidates an early check-in with audit, then updates the
     * same schedule.
     */
    @Transactional("scheduleTransactionManager")
    public void decide(AuthenticatedAccount principal, long requestId, boolean approve, String rejectReason) {
        LiveActor actor = active(principal);
        if (!actor.role().equals("ADMIN") && !actor.role().equals("HR"))
            throw forbidden();
        var rows = db.query("""
                SELECT r.employee_id,r.status,r.submitted_at,d.schedule_id,d.original_start,d.original_end,
                       d.proposed_shift_id,d.proposed_start,d.proposed_end,d.original_row_version
                FROM dbo.employee_requests r WITH(UPDLOCK,HOLDLOCK)
                JOIN dbo.schedule_change_request_details d ON d.request_id=r.request_id
                WHERE r.request_id=? AND r.request_type='DOI_LICH'
                """, (rs, i) -> new Object[] { rs.getLong(1), rs.getString(2), rs.getLong(4),
                rs.getObject(5, LocalDateTime.class), rs.getObject(6, LocalDateTime.class),
                rs.getObject(7, Long.class), rs.getObject(8, LocalDateTime.class), rs.getObject(9, LocalDateTime.class),
                rs.getBytes(10) }, requestId);
        if (rows.size() != 1)
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Request not found");
        Object[] r = rows.get(0);
        long employee = (Long) r[0], schedule = (Long) r[2];
        if (!"CHO_DUYET".equals(r[1]))
            throw conflict("Request already closed");
        if (Objects.equals(actor.employeeId(), employee))
            throw forbidden();
        LocalDateTime now = LocalDateTime.now(ZoneId.of("Asia/Ho_Chi_Minh"));
        var currentRows = db.query(
                """
                        SELECT schedule_id,employee_id,shift_id,work_date,planned_start,planned_end,planned_break_minutes,note,row_version
                        FROM dbo.work_schedules WITH(UPDLOCK,HOLDLOCK) WHERE schedule_id=?
                        """,
                (rs, i) -> new Object[] { new ScheduleView(rs.getLong(1), rs.getLong(2), rs.getObject(3, Long.class),
                        rs.getDate(4).toLocalDate(), rs.getTimestamp(5).toLocalDateTime(),
                        rs.getTimestamp(6).toLocalDateTime(), rs.getInt(7), rs.getString(8)), rs.getBytes(9) },
                schedule);
        if (currentRows.size() != 1)
            throw conflict("Original schedule no longer exists");
        ScheduleView oldSchedule = (ScheduleView) currentRows.get(0)[0];
        // Authorize BOTH approval and rejection with live write-scope checks.
        ScheduleInput authorize = new ScheduleInput(employee, (Long) r[5], (LocalDateTime) r[6],
                (LocalDateTime) r[7], 0, "Reviewed schedule change");
        writePermission.requireUpdate(principal, oldSchedule, authorize);
        if (!now.isBefore((LocalDateTime) r[3])) {
            expire(requestId);
            audit(actor.accountId(), requestId, "SCHEDULE_CHANGE_EXPIRED", "Original shift started");
            return;
        }
        if (!approve) {
            if (rejectReason == null || rejectReason.isBlank())
                throw conflict("Rejection reason required");
            finish(requestId, actor.accountId(), "TU_CHOI", rejectReason);
            audit(actor.accountId(), requestId, "SCHEDULE_CHANGE_REJECT", rejectReason);
            return;
        }
        if (!Arrays.equals((byte[]) currentRows.get(0)[1], (byte[]) r[8]))
            throw conflict("Original schedule changed since submission");
        if (!oldSchedule.plannedStart().equals(r[3]) || !oldSchedule.plannedEnd().equals(r[4])
                || oldSchedule.employeeId() != employee)
            throw conflict("Original schedule differs from snapshot");
        // Keep old early check-in as an immutable invalidated historical attendance
        // row.
        var attendance = db.query("""
                SELECT attendance_id,actual_start,actual_end,verification_status
                FROM dbo.attendance_records WITH(UPDLOCK,HOLDLOCK)
                WHERE schedule_id=? AND invalidated_at IS NULL
                """, (rs, i) -> new Object[] { rs.getLong(1), rs.getObject(2, LocalDateTime.class),
                rs.getObject(3, LocalDateTime.class), rs.getString(4) }, schedule);
        if (attendance.size() > 1)
            throw conflict("Multiple active clock entries found");
        if (attendance.size() == 1) {
            Object[] clock = attendance.get(0);
            if (!"CHUA_XAC_NHAN".equals(clock[3]) || clock[1] == null || clock[2] != null
                    || !((LocalDateTime) clock[1]).isBefore(oldSchedule.plannedStart()))
                throw conflict("Only early, unconfirmed check-in may be invalidated");
            long id = (Long) clock[0];
            String before = db.queryForObject(
                    "SELECT (SELECT * FROM dbo.attendance_records WHERE attendance_id=? FOR JSON PATH,WITHOUT_ARRAY_WRAPPER)",
                    String.class, id);
            int count = db.update("""
                    UPDATE dbo.attendance_records SET invalidated_at=SYSUTCDATETIME(),invalidated_by=?,
                    invalidation_reason=?,updated_by=?,updated_at=SYSUTCDATETIME()
                    WHERE attendance_id=? AND invalidated_at IS NULL AND verification_status='CHUA_XAC_NHAN'
                    """, actor.accountId(), "Approved schedule change request " + requestId, actor.accountId(), id);
            if (count != 1)
                throw conflict("Early clock entry concurrently modified");
            String after = db.queryForObject(
                    "SELECT (SELECT * FROM dbo.attendance_records WHERE attendance_id=? FOR JSON PATH,WITHOUT_ARRAY_WRAPPER)",
                    String.class, id);
            db.update(
                    """
                            INSERT dbo.audit_logs(actor_account_id,action,target_table,target_record_key,reason,before_data_json,after_data_json)
                            VALUES(?,'ATTENDANCE_INVALIDATE_FOR_SCHEDULE_CHANGE','attendance_records',?,?,?,?)
                            """,
                    actor.accountId(), Long.toString(id), "Request " + requestId, before, after);
        }
        var windows = db.query(
                "SELECT break_start,break_end FROM dbo.schedule_change_request_breaks WHERE request_id=? ORDER BY break_start",
                (rs, i) -> new TimedBreak(rs.getObject(1, LocalDateTime.class), rs.getObject(2, LocalDateTime.class)),
                requestId);
        long mins = Duration.between((LocalDateTime) r[6], (LocalDateTime) r[7]).toMinutes()
                - windows.stream().mapToLong(w -> Duration.between(w.start(), w.end()).toMinutes()).sum();
        if (mins < 0 || mins > Integer.MAX_VALUE)
            throw conflict("Invalid net time");
        // Delegate actual authorization/overlap/12h checks to existing detailed
        // service.
        ScheduleInput input = new ScheduleInput(employee, (Long) r[5],
                (LocalDateTime) r[6], (LocalDateTime) r[7],
                (int) (Duration.between((LocalDateTime) r[6], (LocalDateTime) r[7]).toMinutes() - mins),
                "Approved change request " + requestId);
        detailed.updateSchedule(principal, schedule, new ScheduleWithBreaks(input, windows));
        finish(requestId, actor.accountId(), "DA_DUYET", null);
        audit(actor.accountId(), requestId, "SCHEDULE_CHANGE_APPROVE", "Approved");
    }

    private void expire(long id) {
        db.update(
                "UPDATE dbo.employee_requests SET status='HET_HIEU_LUC',processed_at=SYSUTCDATETIME() WHERE request_id=? AND status='CHO_DUYET'",
                id);
    }

    private void finish(long id, long actor, String status, String reason) {
        db.update(
                """
                        UPDATE dbo.employee_requests SET status=?,processed_by=?,processed_at=SYSUTCDATETIME(),rejection_reason=?
                        WHERE request_id=? AND status='CHO_DUYET'
                        """,
                status, actor, reason, id);
    }

    private void audit(long actor, long id, String action, String reason) {
        db.update("""
                INSERT dbo.audit_logs(actor_account_id,action,target_table,target_record_key,reason)
                VALUES(? ,?,'employee_requests',?,?)
                """, actor, action, Long.toString(id), reason);
    }
}
