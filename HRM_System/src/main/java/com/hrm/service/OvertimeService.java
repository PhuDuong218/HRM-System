package com.hrm.service;

import static com.hrm.dto.OvertimeDTOs.*;
import com.hrm.security.AuthenticatedAccount;
import com.hrm.service.OvertimePermissionService.VerifiedActor;
import java.time.*;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * Uses the approved hrm_test migration (DA_HUY and overtime_breaks).
 * All write operations require transaction and audit.
 */
@Service
public class OvertimeService {
    private final JdbcTemplate db;
    private final OvertimePermissionService perms;
    private final OvertimeDailyLedger ledger;
    private final OvertimeRecognizedMinutes recognition;
    private static final String FIELDS = "o.overtime_id,o.employee_id,o.request_code,o.requested_start,o.requested_end,o.reason,o.status,o.processed_by,o.rejection_reason,o.actual_start,o.actual_end,o.actual_break_minutes,o.confirmed_by";
    private static final RowMapper<OvertimeView> MAPPER = (rs, i) -> new OvertimeView(rs.getLong(1), rs.getLong(2),
            rs.getString(3), rs.getObject(4, LocalDateTime.class), rs.getObject(5, LocalDateTime.class),
            rs.getString(6), rs.getString(7), rs.getObject(8, Long.class), rs.getString(9),
            rs.getObject(10, LocalDateTime.class), rs.getObject(11, LocalDateTime.class), rs.getInt(12),
            rs.getObject(13, Long.class), null, null, null);

    public OvertimeService(JdbcTemplate db, OvertimePermissionService perms, OvertimeDailyLedger ledger,
            OvertimeRecognizedMinutes recognition) {
        this.recognition = recognition;
        this.db = db;
        this.perms = perms;
        this.ledger = ledger;
    }

    private static ResponseStatusException bad(String x) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, x);
    }

    private static ResponseStatusException conflict(String x) {
        return new ResponseStatusException(HttpStatus.CONFLICT, x);
    }

    private static void need(boolean ok, String err) {
        if (!ok)
            throw conflict(err);
    }

    private OvertimeView load(long id) {
        var rows = db.query("SELECT " + FIELDS + " FROM dbo.overtime_requests o WHERE o.overtime_id=?", MAPPER, id);
        if (rows.isEmpty())
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "OT not found");
        return rows.get(0);
    }

    private OvertimeView locked(long id) {
        OvertimeView v = load(id);
        lockEmployee(v.employeeId());
        return load(id);
    }

    private void lockEmployee(long employee) {
        var rows = db.query("SELECT employee_id FROM dbo.employees WITH (UPDLOCK,HOLDLOCK) WHERE employee_id=?",
                (r, i) -> r.getLong(1), employee);
        if (rows.size() != 1)
            throw bad("Unknown employee");
    }

    private String snapshot(long id) {
        return db.queryForObject(
                "SELECT (SELECT * FROM dbo.overtime_requests WHERE overtime_id=? FOR JSON PATH, WITHOUT_ARRAY_WRAPPER)",
                String.class, id);
    }

    private void audit(long actor, long id, String action, String reason, String before) {
        db.update(
                "INSERT dbo.audit_logs(actor_account_id,action,target_table,target_record_key,reason,before_data_json,after_data_json) VALUES(?,?,'overtime_requests',?,?,?,?)",
                actor, action, Long.toString(id), reason, before, snapshot(id));
    }

    private void checkRange(long employee, LocalDateTime start, LocalDateTime end, long skip) {
        OvertimePolicy.validateRequest(start, end, "OT");
        Long overlaps = db.queryForObject(
                "SELECT COUNT_BIG(*) FROM dbo.overtime_requests WITH (HOLDLOCK) WHERE employee_id=? AND overtime_id<>? AND status NOT IN ('TU_CHOI','DA_HUY') AND requested_start<? AND requested_end>?",
                Long.class, employee, skip, end, start);
        need(overlaps != null && overlaps == 0, "Overlapping OT request");
        Long shift = db.queryForObject(
                "SELECT COUNT_BIG(*) FROM dbo.work_schedules WITH (HOLDLOCK) WHERE employee_id=? AND planned_start<? AND planned_end>?",
                Long.class, employee, end, start);
        need(shift != null && shift == 0, "OT overlaps scheduled work");
        LocalDate d = start.toLocalDate();
        while (!d.isAfter(end.toLocalDate())) {
            LocalDateTime lo = d.atStartOfDay(), hi = d.plusDays(1).atStartOfDay();
            long ot = Math.max(0, Duration.between(max(start, lo), min(end, hi)).toMinutes());
            Long regular = db.queryForObject(
                    "SELECT COALESCE(SUM(DATEDIFF_BIG(MINUTE,CASE WHEN planned_start>? THEN planned_start ELSE ? END,CASE WHEN planned_end<? THEN planned_end ELSE ? END)),0) FROM dbo.work_schedules WHERE employee_id=? AND planned_start<? AND planned_end>?",
                    Long.class, lo, lo, hi, hi, employee, hi, lo);
            // Conservative scheduled gross minutes. Never treat planned break as confirmed
            // work.
            Long other = db.queryForObject(
                    "SELECT COALESCE(SUM(DATEDIFF_BIG(MINUTE,CASE WHEN requested_start>? THEN requested_start ELSE ? END,CASE WHEN requested_end<? THEN requested_end ELSE ? END)),0) FROM dbo.overtime_requests WHERE employee_id=? AND overtime_id<>? AND status IN ('CHO_DUYET','DA_DUYET') AND requested_start<? AND requested_end>?",
                    Long.class, lo, lo, hi, hi, employee, skip, hi, lo);
            need((regular == null ? 0 : regular) + (other == null ? 0 : other) + ot <= 720,
                    "Projected daily work exceeds 12 hours; manual review required");
            d = d.plusDays(1);
        }
    }

    private static LocalDateTime min(LocalDateTime a, LocalDateTime b) {
        return a.isBefore(b) ? a : b;
    }

    private static LocalDateTime max(LocalDateTime a, LocalDateTime b) {
        return a.isAfter(b) ? a : b;
    }

    private static void registrationWindow(LocalDateTime start) {
        LocalDate now = LocalDate.now(ZoneId.of("Asia/Ho_Chi_Minh"));
        need(OvertimePolicy.withinSubmissionWindow(start.toLocalDate(), now)
                && OvertimePolicy.withinAdvanceWindow(start.toLocalDate(), now), "OT is outside submission window");
    }

    @Transactional("scheduleTransactionManager")
    public OvertimeView register(AuthenticatedAccount principal, Register body) {
        VerifiedActor a = perms.requireSelfRegistration(principal);
        OvertimePolicy.validateRequest(body.requestedStart(), body.requestedEnd(), body.reason());
        registrationWindow(body.requestedStart());
        lockEmployee(a.employeeId());
        checkRange(a.employeeId(), body.requestedStart(), body.requestedEnd(), -1);
        String code = "OT-" + UUID.randomUUID().toString().replace("-", "").substring(0, 20);
        Long id = db.queryForObject(
                "INSERT INTO dbo.overtime_requests(employee_id,request_code,requested_start,requested_end,reason) OUTPUT INSERTED.overtime_id VALUES(?,?,?,?,?)",
                Long.class, a.employeeId(), code, body.requestedStart(), body.requestedEnd(), body.reason().trim());
        audit(a.accountId(), id, "OT_REGISTER", body.reason(), null);
        return load(id);
    }

    @Transactional("scheduleTransactionManager")
    public OvertimeView update(AuthenticatedAccount principal, long id, Register body) {
        OvertimeView v = locked(id);
        VerifiedActor a = perms.requireOwner(principal, v.employeeId());
        need("CHO_DUYET".equals(v.status()), "Only pending requests are editable");
        OvertimePolicy.validateRequest(body.requestedStart(), body.requestedEnd(), body.reason());
        registrationWindow(body.requestedStart());
        checkRange(v.employeeId(), body.requestedStart(), body.requestedEnd(), id);
        String before = snapshot(id);
        db.update("UPDATE dbo.overtime_requests SET requested_start=?,requested_end=?,reason=? WHERE overtime_id=?",
                body.requestedStart(), body.requestedEnd(), body.reason().trim(), id);
        audit(a.accountId(), id, "OT_EDIT", body.reason(), before);
        return load(id);
    }

    @Transactional("scheduleTransactionManager")
    public OvertimeView decide(AuthenticatedAccount principal, long id, boolean approve, String reason) {
        OvertimeView v = locked(id);
        VerifiedActor a = perms.requireApprove(principal, v.employeeId(), v.requestedStart().toLocalDate());
        need("CHO_DUYET".equals(v.status()), "Only pending requests can be decided");
        if (!approve && (reason == null || reason.isBlank()))
            throw bad("Rejection reason required");
        if (approve)
            checkRange(v.employeeId(), v.requestedStart(), v.requestedEnd(), id);
        if (approve && a.employeeId() != null && a.employeeId() == v.employeeId())
            throw conflict("An OT applicant cannot self-approve");
        String before = snapshot(id);
        db.update(
                "UPDATE dbo.overtime_requests SET status=?,processed_by=?,processed_at=SYSUTCDATETIME(),rejection_reason=? WHERE overtime_id=?",
                approve ? "DA_DUYET" : "TU_CHOI", a.accountId(), approve ? null : reason.trim(), id);
        audit(a.accountId(), id, approve ? "OT_APPROVE" : "OT_REJECT", reason, before);
        return load(id);
    }

    @Transactional("scheduleTransactionManager")
    public OvertimeView recordActual(AuthenticatedAccount principal, long id, Actual body) {
        OvertimeView v = locked(id);
        VerifiedActor a = perms.requireOperate(principal, v.employeeId(), v.requestedStart().toLocalDate());
        need("DA_DUYET".equals(v.status()) && v.confirmedBy() == null, "Only unconfirmed approved OT can be recorded");
        if (body.actualStart() == null || body.actualEnd() == null || !body.actualEnd().isAfter(body.actualStart()))
            throw bad("Invalid actual interval");
        int breaks = body.actualBreakMinutes() == null ? 0 : body.actualBreakMinutes();
        if (body.breaks() != null && !body.breaks().isEmpty()) {
            var intervals = new ArrayList<>(body.breaks());
            intervals.sort(Comparator.comparing(BreakInput::breakStart));
            long total = 0;
            LocalDateTime lastEnd = null;
            for (BreakInput b : intervals) {
                if (b.breakStart() == null || b.breakEnd() == null || !b.breakEnd().isAfter(b.breakStart())
                        || b.breakStart().isBefore(body.actualStart()) || b.breakEnd().isAfter(body.actualEnd())
                        || (lastEnd != null && b.breakStart().isBefore(lastEnd)))
                    throw bad("Invalid or overlapping detailed OT breaks");
                total += Duration.between(b.breakStart(), b.breakEnd()).toMinutes();
                lastEnd = b.breakEnd();
            }
            if (total > Integer.MAX_VALUE)
                throw bad("Excessive break duration");
            if (body.actualBreakMinutes() != null && body.actualBreakMinutes() != total)
                throw bad("Break minutes mismatch");
            breaks = (int) total;
        }
        long gross = Duration.between(body.actualStart(), body.actualEnd()).toMinutes();
        if (breaks < 0 || breaks > gross)
            throw bad("Invalid break minutes");
        if (v.actualStart() != null && (body.reason() == null || body.reason().isBlank()))
            throw bad("Correction reason required");
        Long priorOverflow = db.queryForObject(
                "SELECT COUNT_BIG(*) FROM dbo.overtime_request_details d JOIN dbo.employee_requests r ON r.request_id=d.request_id WHERE d.overtime_id=? AND r.request_type='OT_VUOT'",
                Long.class, id);
        need(priorOverflow != null && priorOverflow == 0,
                "Actual OT with overflow decisions is immutable; administrative correction workflow required");
        String before = snapshot(id);
        db.update(
                "UPDATE dbo.overtime_requests SET actual_start=?,actual_end=?,actual_break_minutes=? WHERE overtime_id=?",
                body.actualStart(), body.actualEnd(), breaks, id);
        // Retire previous detailed intervals without hard-delete, then store a new
        // version.
        db.update("UPDATE dbo.overtime_breaks SET is_active=0 WHERE overtime_id=? AND is_active=1", id);
        if (body.breaks() != null) {
            for (BreakInput b : body.breaks()) {
                db.update("INSERT dbo.overtime_breaks(overtime_id,break_start,break_end,created_by) VALUES(?,?,?,?)",
                        id, b.breakStart(), b.breakEnd(), a.accountId());
            }
        }
        audit(a.accountId(), id, "OT_ACTUAL", body.reason(), before);
        return load(id);
    }

    @Transactional("scheduleTransactionManager")
    public OvertimeView confirm(AuthenticatedAccount principal, long id) {
        OvertimeView v = locked(id);
        VerifiedActor a = perms.requireConfirm(principal, v.employeeId(), v.requestedStart().toLocalDate(),
                v.processedBy() == null ? 0 : v.processedBy());
        need("DA_DUYET".equals(v.status()) && v.confirmedBy() == null && v.actualStart() != null
                && v.actualEnd() != null, "OT is not ready for confirmation");
        boolean overflow = v.actualStart().isBefore(v.requestedStart()) || v.actualEnd().isAfter(v.requestedEnd());
        if (overflow) {
            Long pending = db.queryForObject(
                    "SELECT COUNT_BIG(*) FROM dbo.overtime_request_details d JOIN dbo.employee_requests r ON r.request_id=d.request_id WHERE d.overtime_id=? AND r.request_type='OT_VUOT' AND r.status='CHO_DUYET'",
                    Long.class, id);
            need(pending != null && pending == 0, "Pending overflow decision before OT confirmation");
            Long resolved = db.queryForObject(
                    "SELECT COUNT_BIG(*) FROM dbo.overtime_request_details d JOIN dbo.employee_requests r ON r.request_id=d.request_id WHERE d.overtime_id=? AND r.request_type='OT_VUOT' AND r.status IN ('DA_DUYET','TU_CHOI')",
                    Long.class, id);
            need(resolved != null && resolved > 0, "Overflow must be reviewed before confirmation");
        }
        var recognized = recognition.recognize(id, v.actualStart(), v.actualEnd(), v.requestedStart(), v.requestedEnd(),
                v.actualBreakMinutes());
        need(recognized.values().stream().mapToLong(Long::longValue).sum() > 0, "No recognized OT minutes");
        // Daily net is calculated from persisted break intervals; no estimation across
        // midnight.
        var byDay = ledger.netMinutes(id, v.actualStart(), v.actualEnd(), v.actualBreakMinutes());
        need(byDay.values().stream().mapToLong(Long::longValue).sum() > 0, "OT net time must be positive");
        Long regularOverlap = db.queryForObject("""
                SELECT COUNT_BIG(*) FROM dbo.attendance_records a
                WHERE a.employee_id=? AND a.invalidated_at IS NULL AND a.verification_status='DA_XAC_NHAN'
                  AND a.attendance_result='DI_LAM' AND a.actual_end IS NOT NULL
                  AND a.actual_start<? AND a.actual_end>?
                """, Long.class, v.employeeId(), v.actualEnd(), v.actualStart());
        need(regularOverlap != null && regularOverlap == 0, "OT overlaps confirmed regular attendance");
        Long otherOverlap = db.queryForObject("""
                SELECT COUNT_BIG(*) FROM dbo.overtime_requests o
                WHERE o.employee_id=? AND o.overtime_id<>? AND o.status='DA_DUYET'
                  AND o.confirmed_by IS NOT NULL AND o.actual_start<? AND o.actual_end>?
                """, Long.class, v.employeeId(), id, v.actualEnd(), v.actualStart());
        need(otherOverlap != null && otherOverlap == 0, "OT overlaps confirmed OT");
        for (var entry : byDay.entrySet()) {
            LocalDate day = entry.getKey();
            LocalDateTime lo = day.atStartOfDay(), hi = day.plusDays(1).atStartOfDay();
            Long missing = db.queryForObject(
                    """
                            SELECT COUNT_BIG(*) FROM dbo.work_schedules s
                            WHERE s.employee_id=? AND s.planned_start<? AND s.planned_end>?
                            AND NOT EXISTS(SELECT 1 FROM dbo.attendance_records a
                              WHERE a.schedule_id=s.schedule_id AND a.invalidated_at IS NULL AND a.verification_status='DA_XAC_NHAN')
                            """,
                    Long.class, v.employeeId(), hi, lo);
            need(missing != null && missing == 0, "Regular attendance not fully confirmed: " + day);
            // Only completely same-day attendance is safe to attribute using aggregate
            // breaks.
            Long ambiguousRegular = db.queryForObject("""
                    SELECT COUNT_BIG(*) FROM dbo.attendance_records a
                    WHERE a.employee_id=? AND a.invalidated_at IS NULL AND a.verification_status='DA_XAC_NHAN'
                    AND a.attendance_result='DI_LAM' AND a.actual_end IS NOT NULL
                    AND a.actual_start<? AND a.actual_end>?
                    AND (a.actual_start<? OR a.actual_end>?)
                    """, Long.class, v.employeeId(), hi, lo, lo, hi);
            need(ambiguousRegular != null && ambiguousRegular == 0,
                    "Cross-day regular attendance needs separately verified day allocation");
            Long regular = db.queryForObject("""
                    SELECT COALESCE(SUM(DATEDIFF_BIG(MINUTE,a.actual_start,a.actual_end)-a.actual_break_minutes),0)
                    FROM dbo.attendance_records a
                    WHERE a.employee_id=? AND a.invalidated_at IS NULL AND a.verification_status='DA_XAC_NHAN'
                    AND a.attendance_result='DI_LAM' AND a.actual_end IS NOT NULL
                    AND a.actual_start>=? AND a.actual_end<=?
                    """, Long.class, v.employeeId(), lo, hi);
            var others = db.query("""
                    SELECT o.overtime_id,o.actual_start,o.actual_end,o.actual_break_minutes
                    FROM dbo.overtime_requests o
                    WHERE o.employee_id=? AND o.overtime_id<>? AND o.status='DA_DUYET'
                    AND o.confirmed_by IS NOT NULL AND o.confirmed_at IS NOT NULL
                    AND o.actual_start<? AND o.actual_end>?
                    """, (rs, i) -> new Object[] { rs.getLong(1), rs.getObject(2, LocalDateTime.class),
                    rs.getObject(3, LocalDateTime.class), rs.getInt(4) }, v.employeeId(), id, hi, lo);
            long otherMinutes = 0;
            for (var other : others)
                otherMinutes += ledger.netMinutes((Long) other[0],
                        (LocalDateTime) other[1], (LocalDateTime) other[2], (Integer) other[3]).getOrDefault(day, 0L);
            need((regular == null ? 0 : regular) + otherMinutes + entry.getValue() <= 720,
                    "Daily 12-hour net limit exceeded: " + day);
        }
        String before = snapshot(id);
        db.update("UPDATE dbo.overtime_requests SET confirmed_by=?,confirmed_at=SYSUTCDATETIME() WHERE overtime_id=?",
                a.accountId(), id);
        audit(a.accountId(), id, "OT_CONFIRM", null, before);
        return load(id);
    }

    /**
     * Re-open an OT confirmation for a documented correction. An OT that has
     * contributed to a locked payroll period must never be silently reopened.
     * The existing actual interval and break history are preserved; HR can
     * subsequently call recordActual(...) and confirm(...) again.
     */
    @Transactional("scheduleTransactionManager")
    public OvertimeView reopenConfirmation(AuthenticatedAccount principal, long id, String reason) {
        if (reason == null || reason.isBlank())
            throw bad("Reopen reason required");
        if (reason.trim().length() > 1000)
            throw bad("Reopen reason too long");
        OvertimeView current = locked(id);
        VerifiedActor actor = perms.requireOperate(principal, current.employeeId(),
                current.requestedStart().toLocalDate());
        need("DA_DUYET".equals(current.status()) && current.confirmedBy() != null
                && current.actualStart() != null && current.actualEnd() != null,
                "Only confirmed approved OT can be reopened");
        // Payroll batches are authoritative about finality, not only the date.
        // Lock range touches every calendar day intersecting actual OT.
        Long lockedBatches = db.queryForObject("""
                SELECT COUNT_BIG(*) FROM dbo.payroll_batches b WITH (UPDLOCK,HOLDLOCK)
                JOIN dbo.payroll_periods p ON p.period_id=b.period_id
                WHERE b.status='DA_CHOT' AND p.period_start < ? AND p.period_end > ?
                """, Long.class, current.actualEnd().toLocalDate().plusDays(1),
                current.actualStart().toLocalDate());
        need(lockedBatches != null && lockedBatches == 0,
                "Cannot reopen confirmed OT in a locked payroll period");
        Long reviewed = db.queryForObject(
                "SELECT COUNT_BIG(*) FROM dbo.overtime_request_details d JOIN dbo.employee_requests r ON r.request_id=d.request_id WHERE d.overtime_id=? AND r.request_type='OT_VUOT'",
                Long.class, id);
        need(reviewed != null && reviewed == 0,
                "Cannot reopen confirmed OT with overflow requests without a separate amendment process");
        String before = snapshot(id);
        int changed = db.update("""
                UPDATE dbo.overtime_requests
                SET confirmed_by=NULL,confirmed_at=NULL
                WHERE overtime_id=? AND status='DA_DUYET' AND confirmed_by IS NOT NULL
                """, id);
        need(changed == 1, "Concurrent OT confirmation change");
        audit(actor.accountId(), id, "OT_REOPEN_CONFIRMATION", reason.trim(), before);
        return load(id);
    }

    public OvertimeDetail get(AuthenticatedAccount principal, long id) {
        OvertimeView v = load(id);
        perms.requireView(principal, v.employeeId(), v.requestedStart().toLocalDate());
        List<BreakView> rests = db.query(
                "SELECT break_id,break_start,break_end FROM dbo.overtime_breaks WHERE overtime_id=? AND is_active=1 ORDER BY break_start",
                (rs, row) -> new BreakView(rs.getLong(1), rs.getObject(2, LocalDateTime.class),
                        rs.getObject(3, LocalDateTime.class)),
                id);
        return new OvertimeDetail(v, rests, List.of());
    }

    public List<OvertimeView> list(AuthenticatedAccount principal, String status, LocalDate from, LocalDate to,
            int page, int size) {
        var actor = perms.active(principal);
        if (!("ADMIN".equals(actor.roleCode()) || "HR".equals(actor.roleCode()) || "EMPLOYEE".equals(actor.roleCode())))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "OT read denied");
        if (page < 0 || size < 1 || size > 200)
            throw bad("Invalid pagination");
        StringBuilder q = new StringBuilder("SELECT " + FIELDS + " FROM dbo.overtime_requests o WHERE 1=1");
        List<Object> p = new ArrayList<>();
        if (!"ADMIN".equals(actor.roleCode())) {
            if ("HR".equals(actor.roleCode())) {
                q.append(" AND (o.employee_id=? OR ").append(
                        OvertimePermissionService.hrScopeExistsSql("o.employee_id", "CONVERT(date,o.requested_start)"))
                        .append(")");
                p.add(actor.employeeId());
                p.add(actor.accountId());
            } else {
                q.append(" AND o.employee_id=?");
                p.add(actor.employeeId());
            }
        }
        if (status != null && !status.isBlank()) {
            q.append(" AND o.status=?");
            p.add(status);
        }
        if (from != null) {
            q.append(" AND o.requested_start>=?");
            p.add(from.atStartOfDay());
        }
        if (to != null) {
            q.append(" AND o.requested_start<?");
            p.add(to.plusDays(1).atStartOfDay());
        }
        q.append(" ORDER BY o.requested_start DESC,o.overtime_id DESC OFFSET ? ROWS FETCH NEXT ? ROWS ONLY");
        p.add(page * size);
        p.add(size);
        return db.query(q.toString(), MAPPER, p.toArray());
    }

    @Transactional("scheduleTransactionManager")
    public OvertimeView cancel(AuthenticatedAccount principal, long id, String reason) {
        if (reason == null || reason.isBlank())
            throw bad("Cancellation reason required");
        OvertimeView v = locked(id);
        if ("CHO_DUYET".equals(v.status())) {
            perms.requireOwner(principal, v.employeeId());
        } else if ("DA_DUYET".equals(v.status())) {
            throw conflict("Approved OT requires a separate employee cancellation request");
        } else {
            throw conflict("Only pending or approved requests can be cancelled");
        }
        need(v.actualStart() == null && v.actualEnd() == null && v.confirmedBy() == null,
                "Cannot cancel OT with actual work or confirmation");
        String before = snapshot(id);
        int count = db.update(
                "UPDATE dbo.overtime_requests SET status='DA_HUY' WHERE overtime_id=? AND status=? AND actual_start IS NULL AND actual_end IS NULL AND confirmed_by IS NULL",
                id, v.status());
        need(count == 1, "OT cancellation conflict");
        audit(perms.active(principal).accountId(), id, "OT_CANCEL", reason.trim(), before);
        return load(id);
    }

    /**
     * Record a review of actual minutes outside the approved window. This is
     * deliberately NOT a supplemental approval and does not change payable OT.
     * The audit event is the durable review record; no new OT table is needed.
     */
    @Transactional("scheduleTransactionManager")
    public OvertimeView resolveOverflow(AuthenticatedAccount principal, long id, String note) {
        if (note == null || note.isBlank())
            throw bad("Overflow review note required");
        OvertimeView v = locked(id);
        VerifiedActor actor = perms.requireOverflowReview(principal, v.employeeId(),
                v.requestedStart().toLocalDate());
        need("DA_DUYET".equals(v.status()) && v.actualStart() != null && v.actualEnd() != null,
                "No approved request with actual time to review");
        boolean exceeds = v.actualStart().isBefore(v.requestedStart())
                || v.actualEnd().isAfter(v.requestedEnd());
        need(exceeds, "Actual OT has no interval outside approved hours");
        audit(actor.accountId(), id, "OT_OVERFLOW_REVIEW", note.trim(), snapshot(id));
        return load(id);
    }
}
