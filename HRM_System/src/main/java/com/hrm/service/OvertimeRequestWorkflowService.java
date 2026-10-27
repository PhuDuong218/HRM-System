package com.hrm.service;

import com.hrm.security.AuthenticatedAccount;
import com.hrm.service.OvertimePermissionService.VerifiedActor;
import java.time.*;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * OT request decisions share employee_requests and two schema additions.
 * Requires migration 01 and the existing scheduleTransactionManager.
 * All mutating operations lock the OT row, serialized on the employee row.
 */
@Service
public class OvertimeRequestWorkflowService {
    public record Window(LocalDate workDate, LocalDateTime start, LocalDateTime end) {
    }

    public record DecisionWindow(long intervalId, LocalDateTime approvedStart, LocalDateTime approvedEnd) {
    }

    private record Ot(long id, long employee, LocalDateTime start, LocalDateTime end,
            LocalDateTime actualStart, LocalDateTime actualEnd, String status, Long confirmedBy) {
    }

    private record Request(long id, long otId, long employee, String type, String status) {
    }

    private final JdbcTemplate db;
    private final OvertimePermissionService perms;

    public OvertimeRequestWorkflowService(JdbcTemplate db, OvertimePermissionService perms) {
        this.db = db;
        this.perms = perms;
    }

    private static void check(boolean ok, String why) {
        if (!ok)
            throw new ResponseStatusException(HttpStatus.CONFLICT, why);
    }

    private static void validReason(String reason) {
        if (reason == null || reason.isBlank() || reason.length() > 1000)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Reason is required (max 1000)");
    }

    private Ot ot(long id) {
        var found = db.query(
                "SELECT overtime_id,employee_id,requested_start,requested_end,actual_start,actual_end,status,confirmed_by FROM dbo.overtime_requests WHERE overtime_id=?",
                (r, i) -> new Ot(r.getLong(1), r.getLong(2), r.getObject(3, LocalDateTime.class),
                        r.getObject(4, LocalDateTime.class),
                        r.getObject(5, LocalDateTime.class), r.getObject(6, LocalDateTime.class), r.getString(7),
                        r.getObject(8, Long.class)),
                id);
        if (found.size() != 1)
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "OT not found");
        return found.get(0);
    }

    private Ot lockOt(long id) {
        Ot old = ot(id);
        db.queryForObject("SELECT employee_id FROM dbo.employees WITH(UPDLOCK,HOLDLOCK) WHERE employee_id=?",
                Long.class, old.employee());
        db.queryForObject("SELECT overtime_id FROM dbo.overtime_requests WITH(UPDLOCK,HOLDLOCK) WHERE overtime_id=?",
                Long.class, id);
        return ot(id);
    }

    private Request lockRequest(long requestId) {
        var refs = db.query("SELECT d.overtime_id FROM dbo.overtime_request_details d WHERE d.request_id=?",
                (r, i) -> r.getLong(1), requestId);
        if (refs.size() != 1)
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "OT workflow request not found");
        Ot parent = lockOt(refs.get(0));
        var rs = db.query(
                "SELECT r.request_id,r.employee_id,r.request_type,r.status FROM dbo.employee_requests r WITH(UPDLOCK,HOLDLOCK) WHERE r.request_id=?",
                (r, i) -> new Request(r.getLong(1), parent.id(), r.getLong(2), r.getString(3), r.getString(4)),
                requestId);
        check(rs.size() == 1 && rs.get(0).employee() == parent.employee(), "Request mismatch");
        return rs.get(0);
    }

    private void noPending(long overtimeId, String type) {
        Long n = db.queryForObject(
                "SELECT COUNT_BIG(*) FROM dbo.overtime_request_details d JOIN dbo.employee_requests r WITH(UPDLOCK,HOLDLOCK) ON r.request_id=d.request_id WHERE d.overtime_id=? AND r.request_type=? AND r.status='CHO_DUYET'",
                Long.class, overtimeId, type);
        check(n != null && n == 0, "A pending request of this type already exists");
    }

    private void notLocked(LocalDateTime start, LocalDateTime end) {
        Long count = db.queryForObject(
                "SELECT COUNT_BIG(*) FROM dbo.payroll_batches b WITH(UPDLOCK,HOLDLOCK) JOIN dbo.payroll_periods p ON p.period_id=b.period_id WHERE (b.locked_at IS NOT NULL OR b.published_at IS NOT NULL) AND p.period_start<? AND p.period_end>=?",
                Long.class, end.toLocalDate().plusDays(1), start.toLocalDate());
        check(count != null && count == 0, "Payroll period locked or published");
    }

    private String auditBefore(long requestId) {
        return db.queryForObject(
                "SELECT (SELECT * FROM dbo.employee_requests WHERE request_id=? FOR JSON PATH,WITHOUT_ARRAY_WRAPPER)",
                String.class, requestId);
    }

    private void audit(long actor, long requestId, String action, String reason, String before) {
        String after = auditBefore(requestId);
        db.update(
                "INSERT dbo.audit_logs(actor_account_id,action,target_table,target_record_key,reason,before_data_json,after_data_json) VALUES(?,?,'employee_requests',?,?,?,?)",
                actor, action, Long.toString(requestId), reason, before, after);
    }

    private long newRequest(VerifiedActor actor, Ot o, String type, String reason) {
        noPending(o.id(), type);
        String code = "OTR-" + UUID.randomUUID().toString().replace("-", "").substring(0, 20);
        Long id = db.queryForObject(
                "INSERT dbo.employee_requests(employee_id,request_code,request_type,reason,created_by) OUTPUT INSERTED.request_id VALUES(?,?,?,?,?)",
                Long.class,
                o.employee(), code, type, reason.trim(), actor.accountId());
        db.update("INSERT dbo.overtime_request_details(request_id,overtime_id,created_by) VALUES(?,?,?)", id, o.id(),
                actor.accountId());
        audit(actor.accountId(), id, "OT_" + type + "_SUBMIT", reason, null);
        return id;
    }

    @Transactional("scheduleTransactionManager")
    public long requestCancellation(AuthenticatedAccount principal, long overtimeId, String reason) {
        validReason(reason);
        Ot o = lockOt(overtimeId);
        VerifiedActor actor = perms.requireOwner(principal, o.employee());
        check("DA_DUYET".equals(o.status()), "Only approved OT uses two-step cancellation");
        check(LocalDateTime.now(ZoneId.of("Asia/Ho_Chi_Minh")).isBefore(o.start()), "OT already started");
        check(o.actualStart() == null && o.actualEnd() == null && o.confirmedBy() == null, "OT has actual work");
        return newRequest(actor, o, "HUY_OT", reason);
    }

    @Transactional("scheduleTransactionManager")
    public long requestOverflow(AuthenticatedAccount principal, long overtimeId, String reason, List<Window> windows) {
        validReason(reason);
        Ot o = lockOt(overtimeId);
        VerifiedActor actor = perms.requireOwner(principal, o.employee());
        check("DA_DUYET".equals(o.status()) && o.confirmedBy() == null && o.actualStart() != null
                && o.actualEnd() != null, "Actual OT must be recorded and unconfirmed");
        check(windows != null && !windows.isEmpty(), "Overflow windows required");
        // Validate BEFORE sorting: Comparator.comparing(Window::start) otherwise
        // throws NullPointerException for a null entry or null start time.
        for (Window window : windows) {
            if (window == null || window.workDate() == null || window.start() == null
                    || window.end() == null || !window.start().isBefore(window.end())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid overflow window");
            }
        }
        var sorted = new ArrayList<>(windows);
        sorted.sort(Comparator.comparing(Window::start));
        LocalDateTime previousEnd = null;
        for (Window w : sorted) {
            check(w != null && w.workDate() != null && w.start() != null && w.end() != null
                    && w.start().isBefore(w.end()), "Invalid window");
            check(w.workDate().equals(w.start().toLocalDate())
                    && !w.end().isAfter(w.workDate().plusDays(1).atStartOfDay()), "Window crosses day");
            check(!w.start().isBefore(o.actualStart()) && !w.end().isAfter(o.actualEnd()), "Window outside actual OT");
            check(w.end().isBefore(o.start()) || w.start().isAfter(o.end()) || w.end().equals(o.start())
                    || w.start().equals(o.end()), "Window must be wholly outside originally approved interval");
            check(previousEnd == null || !w.start().isBefore(previousEnd), "Overlapping requested windows");
            Long breaks = db.queryForObject(
                    "SELECT COUNT_BIG(*) FROM dbo.overtime_breaks WHERE overtime_id=? AND is_active=1 AND break_start<? AND break_end>?",
                    Long.class, o.id(), w.end(), w.start());
            check(breaks != null && breaks == 0, "Overflow intersects OT break");
            previousEnd = w.end();
        }
        notLocked(o.actualStart(), o.actualEnd());
        long id = newRequest(actor, o, "OT_VUOT", reason);
        for (Window w : sorted)
            db.update(
                    "INSERT dbo.overtime_overflow_intervals(request_id,work_date,requested_start,requested_end,created_by) VALUES(?,?,?,?,?)",
                    id, w.workDate(), w.start(), w.end(), actor.accountId());
        return id;
    }

    /**
     * Read one OT workflow's intervals; caller must own the request or have
     * approval scope.
     */
    @Transactional(value = "scheduleTransactionManager", readOnly = true)
    public List<Map<String, Object>> intervals(AuthenticatedAccount principal, long requestId) {
        var found = db.query(
                "SELECT d.overtime_id,r.employee_id,r.request_type FROM dbo.overtime_request_details d JOIN dbo.employee_requests r ON r.request_id=d.request_id WHERE d.request_id=?",
                (rs, i) -> new Object[] { rs.getLong(1), rs.getLong(2), rs.getString(3) }, requestId);
        if (found.size() != 1)
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Workflow request not found");
        long otId = (Long) found.get(0)[0], employee = (Long) found.get(0)[1];
        Ot o = ot(otId);
        check(o.employee() == employee, "Workflow request owner mismatch");
        var actor = perms.active(principal);
        if (actor.employeeId() == null || actor.employeeId() != employee)
            perms.requireApprove(principal, employee, o.start().toLocalDate());
        else
            perms.requireOwner(principal, employee);
        return db.queryForList(
                "SELECT interval_id,work_date,requested_start,requested_end,approved_start,approved_end FROM dbo.overtime_overflow_intervals WHERE request_id=? ORDER BY work_date,requested_start,interval_id",
                requestId);
    }

    @Transactional("scheduleTransactionManager")
    public void decide(AuthenticatedAccount principal, long requestId, boolean approve, String rejectReason,
            List<DecisionWindow> accepted) {
        Request r = lockRequest(requestId);
        Ot o = ot(r.otId());
        VerifiedActor actor = perms.requireApprove(principal, o.employee(), o.start().toLocalDate());
        check("CHO_DUYET".equals(r.status()), "Request was already processed");
        check("DA_DUYET".equals(o.status()) && o.confirmedBy() == null, "OT no longer eligible");
        if (!approve)
            validReason(rejectReason);
        notLocked(o.actualStart() == null ? o.start() : o.actualStart(),
                o.actualEnd() == null ? o.end() : o.actualEnd());
        if (approve && "HUY_OT".equals(r.type())) {
            check(LocalDateTime.now(ZoneId.of("Asia/Ho_Chi_Minh")).isBefore(o.start()), "OT already started");
            check(o.actualStart() == null && o.actualEnd() == null, "OT already has recorded work");
        }
        if (approve && "OT_VUOT".equals(r.type())) {
            check(o.actualStart() != null && o.actualEnd() != null, "Missing actual OT");
            check(accepted != null && !accepted.isEmpty(), "Accepted intervals required");
            Set<Long> used = new HashSet<>();
            var acceptedSorted = new ArrayList<>(accepted);
            check(acceptedSorted.stream().noneMatch(Objects::isNull), "Decision window missing");
            acceptedSorted.sort(Comparator.comparing(DecisionWindow::approvedStart,
                    Comparator.nullsFirst(Comparator.naturalOrder())));
            LocalDateTime previousApprovedEnd = null;
            for (DecisionWindow d : acceptedSorted) {
                check(d.approvedStart() != null && d.approvedEnd() != null, "Decision window requires both endpoints");
                check(previousApprovedEnd == null || !d.approvedStart().isBefore(previousApprovedEnd),
                        "Approved intervals overlap");
                previousApprovedEnd = d.approvedEnd();
                check(d != null && used.add(d.intervalId()) && d.approvedStart() != null && d.approvedEnd() != null
                        && d.approvedStart().isBefore(d.approvedEnd()), "Invalid decision interval");
                var row = db.query(
                        "SELECT requested_start,requested_end FROM dbo.overtime_overflow_intervals WHERE request_id=? AND interval_id=?",
                        (rs, i) -> new LocalDateTime[] { rs.getObject(1, LocalDateTime.class),
                                rs.getObject(2, LocalDateTime.class) },
                        requestId, d.intervalId());
                check(row.size() == 1 && !d.approvedStart().isBefore(row.get(0)[0])
                        && !d.approvedEnd().isAfter(row.get(0)[1]), "Approved range outside request");
                Long breaks = db.queryForObject(
                        "SELECT COUNT_BIG(*) FROM dbo.overtime_breaks WHERE overtime_id=? AND is_active=1 AND break_start<? AND break_end>?",
                        Long.class, o.id(), d.approvedEnd(), d.approvedStart());
                check(breaks != null && breaks == 0, "Approved range contains break");
                Long duplicates = db.queryForObject(
                        "SELECT COUNT_BIG(*) FROM dbo.overtime_overflow_intervals i JOIN dbo.overtime_request_details detail ON detail.request_id=i.request_id JOIN dbo.employee_requests er ON er.request_id=i.request_id WHERE detail.overtime_id=? AND er.status='DA_DUYET' AND er.request_type='OT_VUOT' AND i.approved_start<? AND i.approved_end>?",
                        Long.class, o.id(), d.approvedEnd(), d.approvedStart());
                check(duplicates != null && duplicates == 0,
                        "Previously approved overflow intersects proposed interval");
                db.update(
                        "UPDATE dbo.overtime_overflow_intervals SET approved_start=?,approved_end=? WHERE interval_id=? AND request_id=?",
                        d.approvedStart(), d.approvedEnd(), d.intervalId(), requestId);
            }
            // As a defense-in-depth rule, do not let a new decision overwrite a previously
            // approved interval.
        }
        // Capture request state before modification. The existing SQL trigger on
        // employee_requests requires the parent OT to be DA_HUY by the time the
        // HUY_OT request is approved. Update the parent FIRST, in this transaction.
        String before = auditBefore(requestId);
        if (approve && "HUY_OT".equals(r.type())) {
            int changed = db.update(
                    "UPDATE dbo.overtime_requests SET status='DA_HUY' WHERE overtime_id=? AND status='DA_DUYET' AND actual_start IS NULL AND actual_end IS NULL AND confirmed_by IS NULL AND confirmed_at IS NULL",
                    o.id());
            check(changed == 1, "Cancellation concurrent conflict");
        }
        int updated = db.update(
                "UPDATE dbo.employee_requests SET status=?,processed_by=?,processed_at=SYSUTCDATETIME(),rejection_reason=? WHERE request_id=? AND status='CHO_DUYET'",
                approve ? "DA_DUYET" : "TU_CHOI", actor.accountId(), approve ? null : rejectReason.trim(), requestId);
        check(updated == 1, "Request was concurrently processed");
        audit(actor.accountId(), requestId, approve ? "OT_REQUEST_APPROVE" : "OT_REQUEST_REJECT",
                approve ? "Approved" : rejectReason, before);
    }
}
