package com.hrm.service;

import com.hrm.dto.AttendanceDTOs.AttendanceCorrection;
import com.hrm.dto.AttendanceDTOs.AttendanceView;
import com.hrm.security.AuthenticatedAccount;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * Approval-gated correction; never changes recorded attendance during
 * submission.
 */
@Service
public class AttendanceCorrectionRequestService {
    private final JdbcTemplate db;
    private final AttendanceService attendance;
    private final AttendancePermissionService permissions;

    public AttendanceCorrectionRequestService(JdbcTemplate db, AttendanceService attendance,
            AttendancePermissionService permissions) {
        this.db = db;
        this.attendance = attendance;
        this.permissions = permissions;
    }

    private ResponseStatusException deny() {
        return new ResponseStatusException(HttpStatus.FORBIDDEN, "Correction access denied");
    }

    private record Actor(long accountId, Long employeeId, String role) {
    }

    private Actor active(AuthenticatedAccount principal) {
        if (principal == null || principal.accountId() == null)
            throw deny();
        var a = db.query("""
                SELECT a.account_id,a.employee_id,r.role_code FROM dbo.accounts a WITH(HOLDLOCK)
                JOIN dbo.roles r ON a.role_id=r.role_id WHERE a.account_id=? AND a.is_active=1
                """, (rs, i) -> new Actor(rs.getLong(1), rs.getObject(2, Long.class), rs.getString(3)),
                principal.accountId());
        if (a.size() != 1 || !Objects.equals(a.get(0).employeeId(), principal.employeeId()) ||
                !a.get(0).role().equals(principal.roleCode()))
            throw deny();
        return a.get(0);
    }

    @Transactional("scheduleTransactionManager")
    public long submit(AuthenticatedAccount principal, long attendanceId, AttendanceCorrection correction) {
        Actor actor = active(principal);
        // Serialize proposals for the same record and reject invalidated history.
        Long lockedId = db.queryForObject(
                "SELECT attendance_id FROM dbo.attendance_records WITH(UPDLOCK,HOLDLOCK) WHERE attendance_id=? AND invalidated_at IS NULL",
                Long.class, attendanceId);
        if (lockedId == null)
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Attendance not found");
        AttendanceView current = attendance.get(attendanceId);
        if (correction == null || correction.correctionReason() == null || correction.correctionReason().isBlank())
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Correction reason required");
        // HR may submit for their own record, but not approve; employee may submit own.
        boolean owner = Objects.equals(actor.employeeId(), current.employeeId());
        if (!owner)
            permissions.requireAttendanceAccess(actor.accountId(), current.employeeId(), current.workDate(),
                    AttendancePermissionService.MANAGE);
        if (!"DA_XAC_NHAN".equals(current.verificationStatus()))
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Only confirmed attendance uses approval workflow");
        Long pending = db.queryForObject("""
                SELECT COUNT_BIG(*) FROM dbo.attendance_correction_request_details d
                JOIN dbo.employee_requests r ON r.request_id=d.request_id
                WHERE d.attendance_id=? AND r.status='CHO_DUYET'
                """, Long.class, attendanceId);
        if (pending != null && pending > 0)
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Pending correction exists");
        String code = "ACR-" + UUID.randomUUID().toString().replace("-", "").substring(0, 20);
        Long id = db.queryForObject("""
                INSERT dbo.employee_requests(employee_id,request_code,request_type,reason,created_by)
                OUTPUT INSERTED.request_id VALUES(?,?,'SUA_CONG',?,?)
                """, Long.class, current.employeeId(), code, correction.correctionReason().trim(), actor.accountId());
        db.update("""
                INSERT dbo.attendance_correction_request_details
                (request_id,attendance_id,original_fingerprint,proposed_start,proposed_end,
                  proposed_break_minutes,proposed_result)
                SELECT ?,attendance_id,
                  HASHBYTES('SHA2_256',CONCAT_WS('|',verification_status,attendance_result,
                    CONVERT(varchar(33),actual_start,126),CONVERT(varchar(33),actual_end,126),
                    actual_break_minutes,confirmed_by,CONVERT(varchar(33),confirmed_at,126))),
                  ?,?,?,? FROM dbo.attendance_records WITH(UPDLOCK,HOLDLOCK)
                  WHERE attendance_id=? AND verification_status='DA_XAC_NHAN'
                """, id, correction.actualStart(), correction.actualEnd(),
                correction.actualBreakMinutes(), correction.attendanceResult(), attendanceId);
        audit(actor.accountId(), id, "ATTENDANCE_CORRECTION_SUBMIT", correction.correctionReason());
        return id;
    }

    @Transactional("scheduleTransactionManager")
    public void decide(AuthenticatedAccount principal, long requestId, boolean approve, String rejectionReason) {
        Actor actor = active(principal);
        var list = db.query("""
                SELECT r.status,r.created_by,r.employee_id,d.attendance_id,d.proposed_start,d.proposed_end,
                       d.proposed_break_minutes,d.proposed_result,r.reason
                FROM dbo.employee_requests r WITH(UPDLOCK,HOLDLOCK)
                JOIN dbo.attendance_correction_request_details d ON d.request_id=r.request_id
                WHERE r.request_id=? AND r.request_type='SUA_CONG'
                """,
                (rs, i) -> new Object[] { rs.getString(1), rs.getObject(2, Long.class), rs.getLong(3), rs.getLong(4),
                        rs.getObject(5, java.time.LocalDateTime.class), rs.getObject(6, java.time.LocalDateTime.class),
                        rs.getInt(7), rs.getString(8), rs.getString(9) },
                requestId);
        if (list.size() != 1)
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Request not found");
        Object[] v = list.get(0);
        if (!"CHO_DUYET".equals(v[0]))
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Request closed");
        if (actor.accountId() == ((Long) v[1]) || Objects.equals(actor.employeeId(), (Long) v[2]))
            throw deny();
        AttendanceView original = attendance.get((Long) v[3]);
        permissions.requireAttendanceAccess(actor.accountId(), original.employeeId(), original.workDate(),
                AttendancePermissionService.MANAGE);
        permissions.requireAttendanceAccess(actor.accountId(), original.employeeId(), original.workDate(),
                AttendancePermissionService.CONFIRM);
        if (!approve && (rejectionReason == null || rejectionReason.isBlank()))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Rejection reason required");
        if (approve) {
            byte[] expected = db.queryForObject(
                    "SELECT original_fingerprint FROM dbo.attendance_correction_request_details WHERE request_id=?",
                    byte[].class, requestId);
            var rows = db.query("""
                    SELECT a.work_date,a.verification_status,
                      HASHBYTES('SHA2_256',CONCAT_WS('|',a.verification_status,a.attendance_result,
                        CONVERT(varchar(33),a.actual_start,126),CONVERT(varchar(33),a.actual_end,126),
                        a.actual_break_minutes,a.confirmed_by,CONVERT(varchar(33),a.confirmed_at,126)))
                    FROM dbo.attendance_records a WITH(UPDLOCK,HOLDLOCK)
                    WHERE a.attendance_id=?
                    """, (rs, i) -> new Object[] { rs.getDate(1).toLocalDate(), rs.getString(2), rs.getBytes(3) },
                    (Long) v[3]);
            if (rows.size() != 1 || !"DA_XAC_NHAN".equals(rows.get(0)[1])
                    || !Arrays.equals(expected, (byte[]) rows.get(0)[2]))
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Attendance changed since submission");
            // Never rewrite confirmed attendance inside a payroll period that has been
            // closed.
            Long locked = db.queryForObject(
                    """
                            SELECT COUNT_BIG(*) FROM dbo.payroll_periods p WITH(HOLDLOCK)
                            JOIN dbo.payroll_batches b WITH(HOLDLOCK) ON b.period_id=p.period_id
                            WHERE p.period_start<=? AND p.period_end>=? AND (b.locked_at IS NOT NULL OR b.published_at IS NOT NULL)
                            """,
                    Long.class, rows.get(0)[0], rows.get(0)[0]);
            if (locked == null || locked > 0)
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Payroll locked or unavailable");
            // Refuse correction when a confirmed OT calculation may already depend on this
            // day.
            Long confirmedOt = db.queryForObject("""
                    SELECT COUNT_BIG(*) FROM dbo.overtime_requests o WITH(HOLDLOCK)
                    WHERE o.employee_id=? AND o.confirmed_at IS NOT NULL
                      AND o.requested_start<? AND o.requested_end>?
                    """, Long.class, original.employeeId(),
                    ((java.time.LocalDate) rows.get(0)[0]).plusDays(1).atStartOfDay(),
                    ((java.time.LocalDate) rows.get(0)[0]).atStartOfDay());
            if (confirmedOt == null || confirmedOt > 0)
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "Confirmed overtime requires separate reconciliation");
            if ("DI_LAM".equals(v[7])) {
                Long overlapped = db.queryForObject("""
                        SELECT COUNT_BIG(*) FROM dbo.attendance_records a WITH(HOLDLOCK)
                        WHERE a.employee_id=? AND a.attendance_id<>? AND a.invalidated_at IS NULL
                        AND a.verification_status='DA_XAC_NHAN' AND a.attendance_result='DI_LAM'
                        AND a.actual_end IS NOT NULL AND a.actual_start<? AND a.actual_end>?
                        """, Long.class, original.employeeId(), (Long) v[3], v[5], v[4]);
                if (overlapped == null || overlapped > 0)
                    throw new ResponseStatusException(HttpStatus.CONFLICT,
                            "Correction overlaps other confirmed attendance");
            }
            String before = db.queryForObject(
                    "SELECT (SELECT * FROM dbo.attendance_records WHERE attendance_id=? FOR JSON PATH,WITHOUT_ARRAY_WRAPPER)",
                    String.class, (Long) v[3]);
            int applied = db.update("""
                    UPDATE dbo.attendance_records SET actual_start=?,actual_end=?,actual_break_minutes=?,
                      attendance_result=?,correction_reason=?,updated_by=?,updated_at=SYSUTCDATETIME()
                    WHERE attendance_id=? AND verification_status='DA_XAC_NHAN'
                    """, v[4], v[5], v[6], v[7], v[8], actor.accountId(), v[3]);
            if (applied != 1)
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Attendance concurrently modified");
            String after = db.queryForObject(
                    "SELECT (SELECT * FROM dbo.attendance_records WHERE attendance_id=? FOR JSON PATH,WITHOUT_ARRAY_WRAPPER)",
                    String.class, (Long) v[3]);
            db.update(
                    """
                            INSERT dbo.audit_logs(actor_account_id,action,target_table,target_record_key,reason,before_data_json,after_data_json)
                            VALUES(?,'ATTENDANCE_CORRECTION_APPLIED','attendance_records',?,?,?,?)
                            """,
                    actor.accountId(), Long.toString((Long) v[3]), v[8], before, after);
            int closed = db.update(
                    """
                            UPDATE dbo.employee_requests SET status='DA_DUYET',processed_by=?,processed_at=SYSUTCDATETIME(),rejection_reason=NULL
                            WHERE request_id=? AND status='CHO_DUYET'
                            """,
                    actor.accountId(), requestId);
            if (closed != 1)
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Request concurrently processed");
            audit(actor.accountId(), requestId, "ATTENDANCE_CORRECTION_APPROVE", (String) v[8]);
            return;
        }
        db.update(
                """
                        UPDATE dbo.employee_requests SET status='TU_CHOI',processed_by=?,processed_at=SYSUTCDATETIME(),rejection_reason=?
                        WHERE request_id=? AND status='CHO_DUYET'
                        """,
                actor.accountId(), rejectionReason.trim(), requestId);
        audit(actor.accountId(), requestId, "ATTENDANCE_CORRECTION_REJECT", rejectionReason);
    }

    private void audit(long actor, long request, String action, String reason) {
        db.update("""
                INSERT dbo.audit_logs(actor_account_id,action,target_table,target_record_key,reason)
                VALUES(? ,?,'employee_requests',?,?)
                """, actor, action, Long.toString(request), reason);
    }
}
