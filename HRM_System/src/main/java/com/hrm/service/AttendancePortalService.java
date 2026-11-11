package com.hrm.service;

import com.hrm.dto.AttendanceDTOs.*;
import com.hrm.dto.PageResponse;
import com.hrm.security.AuthenticatedAccount;
import java.time.*;
import java.util.*;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/** Attendance page API. All identities, scopes, clocks and action eligibility are server-owned. */
@Service
public class AttendancePortalService {
    @org.springframework.beans.factory.annotation.Autowired(required=false) private AttendanceNotifications notifications;
    public record Context(String role, boolean mine, boolean manage, LocalDateTime serverNow,
            int checkoutRequestDays, int correctionRequestDays) {}
    public record Row(long scheduleId, Long attendanceId, long employeeId, String employeeLabel,
            String department, String shift, LocalDate workDate, LocalDateTime plannedStart,
            LocalDateTime plannedEnd, int plannedBreakMinutes, String breakDetailStatus,
            LocalDateTime actualStart, LocalDateTime actualEnd, int actualBreakMinutes,
            String attendanceResult, String verificationStatus, Long createdBy, Long updatedBy,
            Long confirmedBy, LocalDateTime confirmedAt, String correctionReason,
            String source, List<String> anomalies, boolean canEdit, boolean canConfirm,
            boolean canUnconfirm, Long entryRequestId, boolean canMarkAbsent, boolean canRequest, boolean canCheckIn, boolean canCheckOut, String version, String enteredByLabel, String updatedByLabel, String confirmedByLabel, boolean checkInReviewed,boolean checkOutReviewed,boolean canReviewCheckIn,boolean canReviewCheckOut,String checkInReviewer,LocalDateTime checkInReviewedAt,String checkOutReviewer,LocalDateTime checkOutReviewedAt, String supplementRequestStatus, String missingClockToRequest, boolean checkoutSupplementTooEarly) {}
    public record Stats(long waiting, long confirmed, long missing, long review, long openRequests) {}
    public record Search(List<Row> items, int page, int size, long totalElements, int totalPages, Stats stats) {}
    public record Break(LocalDateTime start, LocalDateTime end) {}
    public record Audit(String action, String actor, LocalDateTime occurredAt, String reason) {}
    public record Detail(Row row, List<Break> breaks, List<Audit> audit, boolean periodLocked, List<Request> requests) {}
    public record Request(long requestId, String requestCode, String requestType, String status,
            Long attendanceId, long scheduleId, String employeeLabel, LocalDate workDate,
            LocalDateTime originalStart, LocalDateTime originalEnd, int originalBreakMinutes,
            String originalResult, String originalVerification, String originalConfirmer,
            LocalDateTime proposedStart, LocalDateTime proposedEnd, int proposedBreakMinutes,
            String proposedResult, String reason, String sender, LocalDateTime submittedAt,
            LocalDateTime submissionDeadline, String receiver, LocalDateTime receivedAt,
            String processor, LocalDateTime processedAt, String decisionReason, LocalDateTime acceptedEnd,
            boolean canReceive, boolean canDecide, LocalDateTime processingDeadline, LocalDateTime acceptedStart, String adjustmentReason) {}
    public record RequestDetail(Request request, Detail current, List<Audit> audit) {}
    public record Proposal(String type, long attendanceId, LocalDateTime actualStart,
            LocalDateTime actualEnd, int actualBreakMinutes, String attendanceResult, String reason, Long scheduleId) {
        public Proposal(String type,long attendanceId,LocalDateTime start,LocalDateTime end,int minutes,String result,String reason){this(type,attendanceId,start,end,minutes,result,reason,null);}
    }
    public record Decision(boolean approve, LocalDateTime acceptedEnd, String reason,LocalDateTime acceptedStart,String adjustmentReason) {
        public Decision(boolean approve,LocalDateTime end,String reason){this(approve,end,reason,null,null);}
    }
    public Map<String,Object> calendarInfo(AuthenticatedAccount p){actor(p);return deadlinePolicy.calendarInfo();}
    private record Actor(long id, Long employee, String role) {}
    private record Scope(String sql, List<Object> args) {}
    @org.springframework.beans.factory.annotation.Autowired
    private AttendancePortalDeadlinePolicy deadlinePolicy;
    private final JdbcTemplate db;
    private final AttendancePermissionService permissions;
    private final AttendanceService attendance;
    private final AttendanceSelfClockService selfClock;
    private final Clock clock;
    private final int checkoutDays;
    private final int correctionDays;
    public AttendancePortalService(JdbcTemplate db, AttendancePermissionService permissions,
            AttendanceService attendance, AttendanceSelfClockService selfClock,
            @Qualifier("hrmBusinessClock") Clock clock,
            @Value("${hrm.attendance.checkout-request-days:7}") int checkoutDays,
            @Value("${hrm.attendance.correction-request-days:3}") int correctionDays) {
        this.db=db;this.permissions=permissions;this.attendance=attendance;this.selfClock=selfClock;
        this.clock=clock;this.checkoutDays=checkoutDays;this.correctionDays=correctionDays;
        if(checkoutDays<1||correctionDays<1)throw new IllegalArgumentException("Invalid attendance deadline policy");
    }
    private static ResponseStatusException error(HttpStatus status,String text){return new ResponseStatusException(status,text);}
    private LocalDateTime now(){return LocalDateTime.now(clock);}
    private Actor actor(AuthenticatedAccount p){
        if(p==null||p.accountId()==null)throw error(HttpStatus.UNAUTHORIZED,"Vui lòng đăng nhập lại.");
        var rows=db.query("""
            SELECT a.account_id,a.employee_id,r.role_code FROM dbo.accounts a JOIN dbo.roles r ON r.role_id=a.role_id
            WHERE a.account_id=? AND a.is_active=1
            """,(rs,n)->new Actor(rs.getLong(1),rs.getObject(2,Long.class),rs.getString(3)),p.accountId());
        if(rows.size()!=1||!Objects.equals(rows.get(0).employee(),p.employeeId())||!rows.get(0).role().equals(p.roleCode()))
            throw error(HttpStatus.FORBIDDEN,"Quyền tài khoản đã thay đổi.");
        Actor a=rows.get(0);
        if(!Set.of("ADMIN","HR","EMPLOYEE").contains(a.role())||(!a.role().equals("ADMIN")&&a.employee()==null))
            throw error(HttpStatus.FORBIDDEN,"Tài khoản không có quyền chấm công.");
        return a;
    }
    public Context context(AuthenticatedAccount p){Actor a=actor(p);return new Context(a.role(),!a.role().equals("ADMIN"),has(a,AttendancePermissionService.MANAGE),now(),checkoutDays,correctionDays);}
    private boolean has(Actor a,String permission){
        if(a.role().equals("EMPLOYEE"))return false;
        Long n=db.queryForObject("""
          SELECT COUNT_BIG(*) FROM dbo.accounts a JOIN dbo.role_permissions rp ON rp.role_id=a.role_id
          JOIN dbo.permissions p ON p.permission_id=rp.permission_id WHERE a.account_id=? AND p.permission_code=?
          """,Long.class,a.id(),permission);return n!=null&&n>0;
    }
    private Scope scope(Actor a, boolean mine){
        if(mine){if(a.role().equals("ADMIN"))throw error(HttpStatus.FORBIDDEN,"ADMIN không chấm công cá nhân.");return new Scope(" AND s.employee_id=?",new ArrayList<>(List.of(a.employee())));}
        permissions.requirePermission(a.id(),AttendancePermissionService.MANAGE);
        if(a.role().equals("ADMIN"))return new Scope("",new ArrayList<>());
        return new Scope("""
          AND s.employee_id<>?
          AND EXISTS(SELECT 1 FROM dbo.employee_assignments ea JOIN dbo.hr_department_scopes hs ON hs.department_id=ea.department_id
             WHERE ea.employee_id=s.employee_id AND ea.effective_from<=s.work_date AND (ea.effective_to IS NULL OR ea.effective_to>s.work_date)
             AND hs.hr_account_id=? AND hs.revoked_at IS NULL AND hs.effective_from<=SYSUTCDATETIME()
             AND (hs.effective_to IS NULL OR hs.effective_to>SYSUTCDATETIME()))
          """,new ArrayList<>(List.of(a.employee(),a.id())));
    }
    private static final String FROM="""
      FROM dbo.work_schedules s JOIN dbo.employees e ON e.employee_id=s.employee_id
      LEFT JOIN dbo.work_shifts w ON w.shift_id=s.shift_id
      LEFT JOIN dbo.attendance_records ar ON ar.schedule_id=s.schedule_id AND ar.invalidated_at IS NULL
      LEFT JOIN dbo.accounts entered_account ON entered_account.account_id=ar.created_by
      LEFT JOIN dbo.accounts updated_account ON updated_account.account_id=ar.updated_by
      LEFT JOIN dbo.accounts confirmed_account ON confirmed_account.account_id=ar.confirmed_by
      OUTER APPLY(SELECT TOP(1) d.department_id,d.department_name FROM dbo.employee_assignments ea
        JOIN dbo.departments d ON d.department_id=ea.department_id WHERE ea.employee_id=s.employee_id
        AND ea.effective_from<=s.work_date AND (ea.effective_to IS NULL OR ea.effective_to>s.work_date)
        ORDER BY ea.effective_from DESC,ea.assignment_id DESC) dep
      WHERE s.cancelled_at IS NULL
      """;
    private static final String FIELDS="""
      SELECT s.schedule_id,ar.attendance_id,s.employee_id,CONCAT(e.employee_code,N' - ',e.full_name) employee_label,
        dep.department_name,CONCAT(w.shift_code,N' - ',w.shift_name) shift_label,s.work_date,s.planned_start,s.planned_end,
        s.planned_break_minutes,s.break_detail_status,ar.actual_start,ar.actual_end,ar.actual_break_minutes,
        ar.attendance_result,ar.verification_status,ar.created_by,ar.updated_by,ar.confirmed_by,ar.confirmed_at,ar.correction_reason,ar.row_version attendance_version,ar.check_in_reviewed_value,ar.check_in_reviewed_at,ar.check_out_reviewed_value,ar.check_out_reviewed_at,
        (SELECT username FROM dbo.accounts WHERE account_id=ar.check_in_reviewed_by) check_in_reviewer,
        (SELECT username FROM dbo.accounts WHERE account_id=ar.check_out_reviewed_by) check_out_reviewer,
        CASE WHEN entered_account.employee_id=ar.employee_id THEN e.full_name COLLATE DATABASE_DEFAULT ELSE entered_account.username COLLATE DATABASE_DEFAULT END entered_by_label,
        updated_account.username updated_by_label,confirmed_account.username confirmed_by_label,
        (SELECT TOP (1) r.status FROM dbo.attendance_portal_requests r
         WHERE r.schedule_id=s.schedule_id AND r.request_type IN ('MISSING_ATTENDANCE','MISSING_CHECKOUT')
         ORDER BY r.submitted_at DESC,r.request_id DESC) supplement_request_status
      """;
    private boolean locked(LocalDate date){Long n=db.queryForObject("""
        SELECT COUNT_BIG(*) FROM dbo.payroll_periods p WITH(HOLDLOCK) JOIN dbo.payroll_batches b WITH(HOLDLOCK) ON b.period_id=p.period_id
        WHERE p.period_start<=? AND p.period_end>=? AND (b.locked_at IS NOT NULL OR b.published_at IS NOT NULL OR b.status='DA_CHOT')
        """,Long.class,date,date);return n==null||n>0;}
    private boolean editedBy(long id,long actor){Long n=db.queryForObject("""
        SELECT COUNT_BIG(*) FROM dbo.audit_logs WHERE target_table='attendance_records' AND target_record_key=?
        AND actor_account_id=? AND action IN ('ATTENDANCE_CREATE','ATTENDANCE_CORRECT','ATTENDANCE_CORRECTION_APPLIED','ATTENDANCE_PORTAL_REQUEST_APPLIED')
        """,Long.class,Long.toString(id),actor);return n==null||n>0;}
    private boolean open(long id){Long n=db.queryForObject("SELECT COUNT_BIG(*) FROM dbo.attendance_portal_requests WHERE attendance_id=? AND status IN ('CHO_XU_LY','DANG_XU_LY')",Long.class,id);return n==null||n>0;}
    private Row map(java.sql.ResultSet rs,Actor a,boolean mine)throws java.sql.SQLException{
        long sid=rs.getLong("schedule_id"); Long id=rs.getObject("attendance_id",Long.class);
        LocalDate date=rs.getDate("work_date").toLocalDate();LocalDateTime ps=rs.getObject("planned_start",LocalDateTime.class),pe=rs.getObject("planned_end",LocalDateTime.class);
        LocalDateTime ai=rs.getObject("actual_start",LocalDateTime.class),ao=rs.getObject("actual_end",LocalDateTime.class);
        String result=rs.getString("attendance_result"),verification=rs.getString("verification_status");
        Long creator=rs.getObject("created_by",Long.class),updater=rs.getObject("updated_by",Long.class),confirmer=rs.getObject("confirmed_by",Long.class);
        var anomalies=new ArrayList<String>();
        if("VANG".equals(result))anomalies.add("VANG");
        else {if(ai==null&&now().isAfter(pe))anomalies.add("THIEU_VAO");if(ai!=null&&ao==null&&now().isAfter(pe.plusHours(2)))anomalies.add("THIEU_RA");
          if(ai!=null&&ai.isAfter(ps.plusMinutes(15)))anomalies.add("DI_MUON");if(ao!=null&&ao.isBefore(pe))anomalies.add("VE_SOM");
          if(ai!=null&&ai.isBefore(ps)||ao!=null&&ao.isAfter(pe))anomalies.add("NGOAI_LICH");}
        boolean periodLocked=locked(date),pending=openSchedule(sid);
        boolean access=!mine; if(access)try{permissions.requireAttendanceAccess(a.id(),rs.getLong("employee_id"),date,AttendancePermissionService.MANAGE);}catch(ResponseStatusException ex){if(ex.getStatusCode()!=HttpStatus.FORBIDDEN)throw ex;access=false;}
        if(ai!=null&&ao!=null&&Boolean.TRUE.equals(db.queryForObject("SELECT CASE WHEN EXISTS(SELECT 1 FROM dbo.attendance_records a WHERE a.employee_id=? AND a.attendance_id<>? AND a.invalidated_at IS NULL AND a.actual_start<? AND a.actual_end>?) THEN CAST(1 AS bit) ELSE CAST(0 AS bit) END",Boolean.class,rs.getLong("employee_id"),id,ao,ai)))anomalies.add("CHONG_LAN");
        boolean independent=!a.role().equals("HR")||(creator!=null&&!Objects.equals(creator,a.id())&&!Objects.equals(updater,a.id())&&!editedBy(id==null?0:id,a.id()));
        boolean inReviewed=ai!=null&&ai.equals(rs.getObject("check_in_reviewed_value",LocalDateTime.class))&&rs.getObject("check_in_reviewed_at")!=null;
        boolean outReviewed=ao!=null&&ao.equals(rs.getObject("check_out_reviewed_value",LocalDateTime.class))&&rs.getObject("check_out_reviewed_at")!=null;
        boolean reviewAccess=access&&id!=null&&!periodLocked&&!pending&&independent&&has(a,AttendancePermissionService.CONFIRM)&&"CHUA_XAC_NHAN".equals(verification);
        boolean canConfirm=access&&id!=null&&!periodLocked&&!pending&&independent&&has(a,AttendancePermissionService.CONFIRM)
          &&"CHUA_XAC_NHAN".equals(verification)&&("VANG".equals(result)||ai!=null&&ao!=null&&inReviewed&&outReviewed);
        boolean canUnconfirm=access&&id!=null&&!periodLocked&&!pending&&has(a,AttendancePermissionService.UNCONFIRM)&&"DA_XAC_NHAN".equals(verification)
          &&(!a.role().equals("HR")||!Objects.equals(confirmer,a.id()));
        boolean request=mine&&!periodLocked&&!pending&&((id==null&&now().isAfter(pe)&&!now().isAfter(deadline(pe.toLocalDate(),"MISSING_ATTENDANCE"))))||mine&&id!=null&&!periodLocked&&!pending&&(("CHUA_XAC_NHAN".equals(verification)&&(ao!=null||ai==null&&now().isAfter(pe))&&!now().isBefore(ps)&&!now().isAfter(deadline(pe.toLocalDate(),"UNCONFIRMED_CORRECTION")))
          ||("CHUA_XAC_NHAN".equals(verification)&&ai!=null&&ao==null&&now().isAfter(pe)&&!now().isAfter(deadline(pe.toLocalDate(),"MISSING_CHECKOUT"))));
        Long entryRequestId=null;
        if(access&&id==null&&has(a,AttendancePermissionService.CONFIRM)&&!Objects.equals(a.employee(),rs.getLong("employee_id"))){
            var requestIds=db.queryForList("SELECT TOP(1) request_id FROM dbo.attendance_portal_requests WHERE schedule_id=? AND request_type='MISSING_ATTENDANCE' AND submitted_by<>? AND (status='CHO_XU_LY' OR status='DANG_XU_LY' AND received_by=?) ORDER BY request_id DESC",Long.class,sid,a.id(),a.id());
            if(!requestIds.isEmpty())entryRequestId=requestIds.get(0);
        }
        String source=id==null?"—":Boolean.TRUE.equals(db.queryForObject("SELECT CASE WHEN EXISTS(SELECT 1 FROM dbo.audit_logs WHERE target_table='attendance_records' AND target_record_key=? AND action='ATTENDANCE_SELF_CHECK_IN') THEN CAST(1 AS bit) ELSE CAST(0 AS bit) END",Boolean.class,Long.toString(id)))?"Tự chấm công":"Nhập thủ công / dữ liệu cũ";
        return new Row(sid,id,rs.getLong("employee_id"),rs.getString("employee_label"),rs.getString("department_name"),rs.getString("shift_label"),date,ps,pe,rs.getInt("planned_break_minutes"),rs.getString("break_detail_status"),ai,ao,rs.getInt("actual_break_minutes"),result,verification,creator,updater,confirmer,rs.getObject("confirmed_at",LocalDateTime.class),rs.getString("correction_reason"),source,anomalies,
          access&&!periodLocked&&!pending&&id!=null&&"CHUA_XAC_NHAN".equals(verification),canConfirm,canUnconfirm,entryRequestId,access&&(id==null||!"VANG".equals(result)&&"CHUA_XAC_NHAN".equals(verification)&&(ai==null||ao==null))&&!periodLocked&&!pending&&now().isAfter(deadline(pe.toLocalDate(),"MISSING_ATTENDANCE")),request,
          mine&&id==null&&AttendanceClockPolicy.checkIn(ps,pe,now()).allowed(),mine&&ai!=null&&ao==null&&"CHUA_XAC_NHAN".equals(verification)&&AttendanceClockPolicy.checkOut(ps,pe,ai,now()).allowed(),rs.getBytes("attendance_version")==null?null:Base64.getEncoder().encodeToString(rs.getBytes("attendance_version")),rs.getString("entered_by_label"),rs.getString("updated_by_label"),rs.getString("confirmed_by_label"),inReviewed,outReviewed,reviewAccess&&ai!=null&&!inReviewed,reviewAccess&&ao!=null&&!outReviewed,rs.getString("check_in_reviewer"),rs.getObject("check_in_reviewed_at",LocalDateTime.class),rs.getString("check_out_reviewer"),rs.getObject("check_out_reviewed_at",LocalDateTime.class),mine?rs.getString("supplement_request_status"):null,
          access&&!periodLocked&&!pending?missingClockToRequest(id,verification,result,ai,ao,pe):null,
          mine&&!periodLocked&&!pending&&ao==null&&(id==null||"CHUA_XAC_NHAN".equals(verification))
            &&!now().isBefore(ps)&&!now().isAfter(pe));
    }
    private String missingClockToRequest(Long id, String verification, String result,
            LocalDateTime start, LocalDateTime end, LocalDateTime plannedEnd) {
        if (now().isBefore(plannedEnd) || now().isAfter(deadline(plannedEnd.toLocalDate(), "MISSING_ATTENDANCE"))) {
            return null;
        }
        if (id == null) return "BOTH";
        if (!"CHUA_XAC_NHAN".equals(verification) || !"DI_LAM".equals(result)) return null;
        if (start == null) return end == null ? "BOTH" : "IN";
        return end == null && now().isAfter(plannedEnd) ? "OUT" : null;
    }

    @Transactional("scheduleTransactionManager")
    public void remindMissingClock(AuthenticatedAccount principal, long scheduleId) {
        Actor manager = actor(principal);
        Row row = detail(principal, false, scheduleId).row();
        employeeLock(row.employeeId());
        row = detail(principal, false, scheduleId).row();
        permissions.requireAttendanceAccess(manager.id(), row.employeeId(), row.workDate(), AttendancePermissionService.MANAGE);
        String missing = row.missingClockToRequest();
        if (missing == null) {
            throw error(HttpStatus.CONFLICT, "Ca không còn đủ điều kiện gửi nhắc bổ sung công.");
        }
        if (notifications == null) {
            throw error(HttpStatus.SERVICE_UNAVAILABLE, "Chưa bật thông báo chấm công.");
        }
        notifications.remindMissingClock(row.scheduleId(), row.employeeId(), manager.id(), missing,
            deadline(row.plannedEnd().toLocalDate(), "MISSING_ATTENDANCE"));
    }

    private void range(LocalDate from,LocalDate to,int page){if(from==null||to==null||to.isBefore(from)||to.isAfter(from.plusDays(366))||page<0||page>100000)throw error(HttpStatus.BAD_REQUEST,"Khoảng ngày tối đa 366 ngày, phân trang phải hợp lệ.");}
    private Scope pendingScope(AuthenticatedAccount p) {
        Scope scoped=scope(actor(p),false);
        return new Scope(FROM+scoped.sql()+" AND ar.verification_status='CHUA_XAC_NHAN'",scoped.args());
    }
    @Transactional(value="scheduleTransactionManager",readOnly=true)
    public long pendingCount(AuthenticatedAccount p) {
        var pending=pendingScope(p);
        return db.queryForObject("SELECT COUNT_BIG(*) "+pending.sql(),Long.class,pending.args().toArray());
    }
    @Transactional(value="scheduleTransactionManager",readOnly=true)
    public Search pending(AuthenticatedAccount p,int page) {
        if(page<0||page>100000)throw error(HttpStatus.BAD_REQUEST,"Phân trang không hợp lệ.");
        var a=actor(p);var pending=pendingScope(p);
        long total=db.queryForObject("SELECT COUNT_BIG(*) "+pending.sql(),Long.class,pending.args().toArray());
        var args=new ArrayList<Object>(pending.args());args.add(page*20);args.add(20);
        var rows=db.query(FIELDS+pending.sql()+" ORDER BY s.work_date,s.schedule_id OFFSET ? ROWS FETCH NEXT ? ROWS ONLY",(rs,n)->map(rs,a,false),args.toArray());
        return new Search(rows,page,20,total,(int)((total+19)/20),null);
    }
    @Transactional(value="scheduleTransactionManager",readOnly=true)
    public Search search(AuthenticatedAccount p,boolean mine,LocalDate from,LocalDate to,String q,Long department,String verification,String anomaly,int page){
        Actor a=actor(p);range(from,to,page);Scope scope=scope(a,mine);
        StringBuilder where=new StringBuilder(FROM).append(scope.sql()).append(" AND s.work_date BETWEEN ? AND ?");
        List<Object> args=new ArrayList<>(scope.args());args.add(from);args.add(to);
        if(q!=null&&!q.isBlank()){if(q.length()>200||q.trim().matches("-\\d+(\\.\\d+)?"))throw error(HttpStatus.BAD_REQUEST,"Mã hoặc tên nhân viên không hợp lệ.");String normalized=java.text.Normalizer.normalize(q.trim().replaceAll("\\s+"," ").replace('—','-').replace('–','-'),java.text.Normalizer.Form.NFD).replaceAll("\\p{M}+","").replace('đ','d').replace('Đ','D');String text="%"+normalized.replace("[","[[]").replace("%","[%]").replace("_","[_]")+"%";where.append(" AND (e.employee_code COLLATE Latin1_General_100_CI_AI LIKE ? OR REPLACE(REPLACE(CONCAT(e.employee_code,N' - ',e.full_name),N'đ',N'd'),N'Đ',N'D') COLLATE Latin1_General_100_CI_AI LIKE ?)");args.add(text);args.add(text);}
        if(department!=null){if(department<=0)throw error(HttpStatus.BAD_REQUEST,"Phòng ban không hợp lệ.");where.append(" AND dep.department_id=?");args.add(department);}
        if(verification!=null&&!verification.isBlank()){if(!Set.of("CHUA_XAC_NHAN","DA_XAC_NHAN","CHUA_CO_CONG","CHUA_HOAN_TAT").contains(verification))throw error(HttpStatus.BAD_REQUEST,"Trạng thái không hợp lệ.");if(verification.equals("CHUA_CO_CONG"))where.append(" AND ar.attendance_id IS NULL");else if(verification.equals("CHUA_HOAN_TAT"))where.append(" AND (ar.attendance_id IS NULL OR ar.verification_status='CHUA_XAC_NHAN')");else {where.append(" AND ar.verification_status=?");args.add(verification);}}
        String late="ar.actual_start>DATEADD(minute,15,s.planned_start)",early="ar.actual_end<s.planned_end",mi="ar.actual_start IS NULL AND s.planned_end<?",mo="ar.actual_start IS NOT NULL AND ar.actual_end IS NULL AND DATEADD(hour,2,s.planned_end)<?";
        if(anomaly!=null&&!anomaly.isBlank()){switch(anomaly){case "DI_MUON"->where.append(" AND ").append(late);case "VE_SOM"->where.append(" AND ").append(early);case "VANG"->where.append(" AND ar.attendance_result='VANG'");case "THIEU_GIO"->{where.append(" AND ( (").append(mi).append(") OR (").append(mo).append(")) AND (ar.attendance_result IS NULL OR ar.attendance_result<>'VANG')");args.add(now());args.add(now());}default->throw error(HttpStatus.BAD_REQUEST,"Bất thường không hợp lệ.");}}
        Long count=db.queryForObject("SELECT COUNT_BIG(*) "+where,Long.class,args.toArray());long total=count==null?0:count;
        List<Object> paged=new ArrayList<>(args);paged.add(page*20);paged.add(20);
        List<Row> items=db.query(FIELDS+where+" ORDER BY s.work_date DESC,s.schedule_id DESC OFFSET ? ROWS FETCH NEXT ? ROWS ONLY",(rs,n)->map(rs,a,mine),paged.toArray());
        List<Object> statArgs=new ArrayList<>();statArgs.add(now());statArgs.add(now());statArgs.addAll(args);
        Stats stats=db.queryForObject("""
          SELECT COALESCE(SUM(CASE WHEN ar.verification_status='CHUA_XAC_NHAN' THEN CAST(1 AS bigint) ELSE 0 END),0),
          COALESCE(SUM(CASE WHEN ar.verification_status='DA_XAC_NHAN' THEN CAST(1 AS bigint) ELSE 0 END),0),
          COALESCE(SUM(CASE WHEN (ar.attendance_result IS NULL OR ar.attendance_result<>'VANG') AND
            ((ar.actual_start IS NULL AND s.planned_end<?) OR (ar.actual_start IS NOT NULL AND ar.actual_end IS NULL AND DATEADD(hour,2,s.planned_end)<?)) THEN CAST(1 AS bigint) ELSE 0 END),0),
          COALESCE(SUM(CASE WHEN ar.actual_start>DATEADD(minute,15,s.planned_start) OR ar.actual_end<s.planned_end OR ar.attendance_result='VANG' THEN CAST(1 AS bigint) ELSE 0 END),0)
          """+where,(rs,n)->new Stats(rs.getLong(1),rs.getLong(2),rs.getLong(3),rs.getLong(4),openCount(a,mine)),statArgs.toArray());
        return new Search(items,page,20,total,(int)((total+19)/20),stats);
    }
    private List<Audit> auditList(String table,long id){return db.query("""
        SELECT a.action,COALESCE(u.username,N'Hệ thống'),a.occurred_at,a.reason FROM dbo.audit_logs a
        LEFT JOIN dbo.accounts u ON u.account_id=a.actor_account_id WHERE a.target_table=? AND a.target_record_key=? ORDER BY a.occurred_at DESC,a.audit_id DESC
        """,(rs,n)->new Audit(rs.getString(1),rs.getString(2),rs.getObject(3,LocalDateTime.class),rs.getString(4)),table,Long.toString(id));}
    @Transactional(value="scheduleTransactionManager",readOnly=true)
    public Detail detail(AuthenticatedAccount p,boolean mine,long scheduleId){Actor a=actor(p);Scope scope=scope(a,mine);List<Object> args=new ArrayList<>(scope.args());args.add(scheduleId);
        var rows=db.query(FIELDS+FROM+scope.sql()+" AND s.schedule_id=?",(rs,n)->map(rs,a,mine),args.toArray());if(rows.size()!=1)throw error(HttpStatus.NOT_FOUND,"Không tìm thấy lịch trong phạm vi được xem.");Row row=rows.get(0);
        var breaks=db.query("SELECT break_start,break_end FROM dbo.work_schedule_breaks WHERE schedule_id=? AND superseded_at IS NULL ORDER BY break_start",(rs,n)->new Break(rs.getObject(1,LocalDateTime.class),rs.getObject(2,LocalDateTime.class)),scheduleId);
        var related=db.query(REQUEST_FIELDS+REQUEST_FROM+" AND s.schedule_id=? ORDER BY r.submitted_at DESC,r.request_id DESC",(rs,n)->mapRequest(rs,a),scheduleId);
        var history=new ArrayList<Audit>();
        if(row.attendanceId()!=null)history.addAll(auditList("attendance_records",row.attendanceId()));
        for(Request request:related)history.addAll(auditList("attendance_portal_requests",request.requestId()));
        history.sort(Comparator.comparing(Audit::occurredAt).reversed());
        return new Detail(row,breaks,history,locked(row.workDate()),related);
    }
    public List<Map<String,Object>> departments(AuthenticatedAccount p){Actor a=actor(p);scope(a,false);return db.queryForList("""
        SELECT DISTINCT d.department_id departmentId,d.department_name departmentName FROM dbo.departments d
        WHERE ?='ADMIN' OR EXISTS(SELECT 1 FROM dbo.hr_department_scopes h WHERE h.department_id=d.department_id AND h.hr_account_id=? AND h.revoked_at IS NULL
          AND h.effective_from<=SYSUTCDATETIME() AND (h.effective_to IS NULL OR h.effective_to>SYSUTCDATETIME())) ORDER BY departmentName
        """,a.role(),a.id());}
    private void employeeLock(long id){db.queryForObject("SELECT employee_id FROM dbo.employees WITH(UPDLOCK,HOLDLOCK) WHERE employee_id=?",Long.class,id);}
    private void attendanceLock(long id){var ids=db.queryForList("SELECT attendance_id FROM dbo.attendance_records WITH(UPDLOCK,HOLDLOCK) WHERE attendance_id=? AND invalidated_at IS NULL",Long.class,id);if(ids.size()!=1)throw error(HttpStatus.NOT_FOUND,"Bản ghi công không còn hiệu lực.");}
    private void writable(LocalDate date){if(locked(date))throw error(HttpStatus.CONFLICT,"Kỳ công/lương đã khóa hoặc công bố; không thể thay đổi.");}
    private static void reason(String reason){if(reason==null||reason.isBlank()||reason.length()>1000)throw error(HttpStatus.BAD_REQUEST,"Lý do bắt buộc, tối đa 1000 ký tự.");}
    private void interval(long employee,long excluded,LocalDateTime start,LocalDateTime end,int breaks,String result){
        if(!Set.of("DI_LAM","VANG").contains(result==null?"":result)||breaks<0)throw error(HttpStatus.BAD_REQUEST,"Kết quả công hoặc phút nghỉ không hợp lệ.");
        if(result.equals("VANG")){if(start!=null||end!=null||breaks!=0)throw error(HttpStatus.BAD_REQUEST,"Công vắng không có giờ vào/ra hoặc phút nghỉ.");return;}
        if(start==null||end!=null&&!end.isAfter(start)||end==null&&breaks!=0||end!=null&&Duration.between(start,end).toMinutes()<breaks||start.isAfter(now())||end!=null&&end.isAfter(now()))throw error(HttpStatus.BAD_REQUEST,"Giờ vào/ra hoặc phút nghỉ không hợp lệ; không nhập giờ tương lai.");
        Long closed=db.queryForObject("""
          SELECT COUNT_BIG(*) FROM dbo.payroll_periods p WITH(HOLDLOCK) JOIN dbo.payroll_batches b WITH(HOLDLOCK) ON b.period_id=p.period_id
          WHERE p.period_start<=? AND p.period_end>=? AND (b.locked_at IS NOT NULL OR b.published_at IS NOT NULL OR b.status='DA_CHOT')
          """,Long.class,end==null?start.toLocalDate():end.minusNanos(1).toLocalDate(),start.toLocalDate());
        if(closed==null||closed>0)throw error(HttpStatus.CONFLICT,"Khoảng giờ thực tế đi qua kỳ công/lương đã khóa.");
        if(end!=null){Long n=db.queryForObject("SELECT COUNT_BIG(*) FROM dbo.attendance_records WITH(UPDLOCK,HOLDLOCK) WHERE employee_id=? AND attendance_id<>? AND invalidated_at IS NULL AND actual_start<? AND actual_end>?",Long.class,employee,excluded,end,start);if(n==null||n>0)throw error(HttpStatus.CONFLICT,"Giờ công chồng lấn bản ghi khác.");}
    }
    @Transactional("scheduleTransactionManager")
    public AttendanceView manual(AuthenticatedAccount p,AttendanceInput body){Actor a=actor(p);if(body==null||body.employeeId()==null||body.scheduleId()==null||body.workDate()==null||body.employeeId()<=0||body.scheduleId()<=0)throw error(HttpStatus.BAD_REQUEST,"Phải chọn lịch làm việc.");reason(body.correctionReason());employeeLock(body.employeeId());var d=detail(p,false,body.scheduleId());if(!d.row().canEdit()||d.row().attendanceId()!=null)throw error(HttpStatus.CONFLICT,"Lịch đã có công hoặc không cho nhập công.");if(d.row().plannedStart().isAfter(now()))throw error(HttpStatus.CONFLICT,"Ca chưa bắt đầu.");writable(body.workDate());interval(body.employeeId(),0,body.actualStart(),body.actualEnd(),body.actualBreakMinutes(),body.attendanceResult());return attendance.createManual(body,a.id());}
    private void checkVersion(long id,String version){
        byte[] expected;
        try{expected=Base64.getDecoder().decode(version);}catch(Exception ex){throw error(HttpStatus.BAD_REQUEST,"Phiên bản công không hợp lệ.");}
        byte[] current=db.queryForObject("SELECT row_version FROM dbo.attendance_records WHERE attendance_id=?",byte[].class,id);
        if(!Arrays.equals(expected,current))throw error(HttpStatus.CONFLICT,"Công đã được người khác thay đổi. Hãy tải lại trước khi thao tác.");
    }
    @Transactional("scheduleTransactionManager")
    public AttendanceView correctVersioned(AuthenticatedAccount p,long id,AttendanceCorrection body,String version){Actor a=actor(p);AttendanceView old=attendance.get(id);permissions.requireAttendanceAccess(a.id(),old.employeeId(),old.workDate(),AttendancePermissionService.MANAGE);detail(p,false,old.scheduleId());employeeLock(old.employeeId());attendanceLock(id);checkVersion(id,version);return correct(p,id,body);}
    @Transactional("scheduleTransactionManager")
    public AttendanceView verifyVersioned(AuthenticatedAccount p,long id,boolean confirm,String why,String version){Actor a=actor(p);AttendanceView old=attendance.get(id);permissions.requireAttendanceAccess(a.id(),old.employeeId(),old.workDate(),confirm?AttendancePermissionService.CONFIRM:AttendancePermissionService.UNCONFIRM);detail(p,false,old.scheduleId());employeeLock(old.employeeId());attendanceLock(id);checkVersion(id,version);return verify(p,id,confirm,why);}
    @Transactional("scheduleTransactionManager")
    public AttendanceView correct(AuthenticatedAccount p,long id,AttendanceCorrection body){Actor a=actor(p);AttendanceView original=attendance.get(id);employeeLock(original.employeeId());attendanceLock(id);original=attendance.get(id);permissions.requireAttendanceAccess(a.id(),original.employeeId(),original.workDate(),AttendancePermissionService.MANAGE);if(body==null)throw error(HttpStatus.BAD_REQUEST,"Thiếu nội dung chỉnh sửa.");reason(body.correctionReason());writable(original.workDate());detail(p,false,original.scheduleId());if(open(id))throw error(HttpStatus.CONFLICT,"Bản ghi đang có yêu cầu điều chỉnh.");interval(original.employeeId(),id,body.actualStart(),body.actualEnd(),body.actualBreakMinutes(),body.attendanceResult());return attendance.correct(id,body,a.id());}
    @Transactional("scheduleTransactionManager")
    public AttendanceView verify(AuthenticatedAccount p,long id,boolean confirm,String why){Actor a=actor(p);AttendanceView current=attendance.get(id);employeeLock(current.employeeId());attendanceLock(id);current=attendance.get(id);permissions.requireAttendanceAccess(a.id(),current.employeeId(),current.workDate(),confirm?AttendancePermissionService.CONFIRM:AttendancePermissionService.UNCONFIRM);detail(p,false,current.scheduleId());writable(current.workDate());if(open(id))throw error(HttpStatus.CONFLICT,"Công đang có yêu cầu điều chỉnh chưa kết thúc.");if(confirm){if(a.role().equals("HR")&&editedBy(id,a.id()))throw error(HttpStatus.FORBIDDEN,"HR không được xác nhận bản ghi từng do mình nhập hoặc sửa.");interval(current.employeeId(),id,current.actualStart(),current.actualEnd(),current.actualBreakMinutes(),current.attendanceResult());return attendance.confirm(id,a.id());}reason(why);return attendance.unconfirm(id,why,a.id());}
    @Transactional("scheduleTransactionManager")
    public AttendanceSelfClockService.ClockEvent clock(AuthenticatedAccount p,long scheduleId,boolean checkIn){Actor a=actor(p);if(a.role().equals("ADMIN"))throw error(HttpStatus.FORBIDDEN,"ADMIN không chấm công cá nhân.");var d=detail(p,true,scheduleId);employeeLock(d.row().employeeId());writable(d.row().workDate());return checkIn?selfClock.checkIn(a.id(),scheduleId):selfClock.checkOut(a.id(),scheduleId);}
    private LocalDateTime deadline(LocalDate date,String type){return date.plusDays(!type.equals("CONFIRMED_CORRECTION")?checkoutDays:correctionDays).atTime(23,59,59);}
    private void audit(long actor,long id,String action,String why){db.update("INSERT dbo.audit_logs(actor_account_id,action,target_table,target_record_key,reason) VALUES(?,?,'attendance_portal_requests',?,?)",actor,action,Long.toString(id),why);if(notifications!=null){String event=switch(action){case "ATTENDANCE_REQUEST_SUBMITTED"->"SUBMITTED";case "ATTENDANCE_REQUEST_RECEIVED"->"RECEIVED";case "ATTENDANCE_REQUEST_APPROVED"->"APPROVED";case "ATTENDANCE_REQUEST_REJECTED"->"REJECTED";default->null;};if(event!=null)notifications.publish(id,event,actor);}}
    private Scope requestScope(Actor a,boolean mine){return scope(a,mine);}
    private static final String REQUEST_FROM="""
      FROM dbo.attendance_portal_requests r LEFT JOIN dbo.attendance_records ar ON ar.attendance_id=r.attendance_id
      JOIN dbo.work_schedules s ON s.schedule_id=r.schedule_id JOIN dbo.employees e ON e.employee_id=s.employee_id
      LEFT JOIN dbo.accounts sender ON sender.account_id=r.submitted_by LEFT JOIN dbo.accounts receiver ON receiver.account_id=r.received_by
      LEFT JOIN dbo.accounts processor ON processor.account_id=r.processed_by LEFT JOIN dbo.accounts confirmer ON confirmer.account_id=r.original_confirmed_by
      WHERE 1=1
      """;
    private long openCount(Actor a,boolean mine){Scope s=requestScope(a,mine);Long n=db.queryForObject("SELECT COUNT_BIG(*) "+REQUEST_FROM+s.sql()+" AND r.status IN ('CHO_XU_LY','DANG_XU_LY')",Long.class,s.args().toArray());return n==null?0:n;}
    private boolean requestManager(Actor a,long target,LocalDate date,long sender){if(a.role().equals("HR")&&Boolean.TRUE.equals(db.queryForObject("SELECT CASE WHEN EXISTS(SELECT 1 FROM dbo.accounts a JOIN dbo.roles r ON r.role_id=a.role_id WHERE a.employee_id=? AND a.is_active=1 AND r.role_code='HR') THEN CAST(1 AS bit) ELSE CAST(0 AS bit) END",Boolean.class,target)))return false;if(a.id()==sender||Objects.equals(a.employee(),target))return false;try{permissions.requireAttendanceAccess(a.id(),target,date,AttendancePermissionService.MANAGE);permissions.requireAttendanceAccess(a.id(),target,date,AttendancePermissionService.CONFIRM);return true;}catch(ResponseStatusException e){if(e.getStatusCode()==HttpStatus.FORBIDDEN)return false;throw e;}}
    private Request mapRequest(java.sql.ResultSet rs,Actor a)throws java.sql.SQLException{
        String state=rs.getString("status");boolean can=requestManager(a,rs.getLong("employee_id"),rs.getDate("work_date").toLocalDate(),rs.getLong("submitted_by"));
        if(a.role().equals("HR")&&(editedBy(rs.getLong("attendance_id"),a.id())||Objects.equals(rs.getObject("original_confirmed_by",Long.class),a.id())&&"DA_XAC_NHAN".equals(rs.getString("original_verification"))))can=false;
        return new Request(rs.getLong("request_id"),rs.getString("request_code"),rs.getString("request_type"),state,rs.getObject("attendance_id",Long.class),rs.getLong("schedule_id"),rs.getString("employee_label"),rs.getDate("work_date").toLocalDate(),rs.getObject("original_start",LocalDateTime.class),rs.getObject("original_end",LocalDateTime.class),rs.getInt("original_break_minutes"),rs.getString("original_result"),rs.getString("original_verification"),rs.getString("confirmer_name"),rs.getObject("proposed_start",LocalDateTime.class),rs.getObject("proposed_end",LocalDateTime.class),rs.getInt("proposed_break_minutes"),rs.getString("proposed_result"),rs.getString("reason"),rs.getString("sender_name"),rs.getObject("submitted_at",LocalDateTime.class),rs.getObject("submission_deadline",LocalDateTime.class),rs.getString("receiver_name"),rs.getObject("received_at",LocalDateTime.class),rs.getString("processor_name"),rs.getObject("processed_at",LocalDateTime.class),rs.getString("decision_reason"),rs.getObject("accepted_end",LocalDateTime.class),can&&state.equals("CHO_XU_LY"),can&&state.equals("DANG_XU_LY")&&Objects.equals(rs.getObject("received_by",Long.class),a.id()),rs.getObject("processing_deadline",LocalDateTime.class)!=null?rs.getObject("processing_deadline",LocalDateTime.class):rs.getDate("work_date").toLocalDate().plusMonths(1).withDayOfMonth(5).atTime(23,59,59),rs.getObject("accepted_start",LocalDateTime.class),rs.getString("adjustment_reason"));
    }
    private static final String REQUEST_FIELDS="SELECT r.*,s.schedule_id,s.work_date,s.employee_id,CONCAT(e.employee_code,N' - ',e.full_name) employee_label,sender.username sender_name,receiver.username receiver_name,processor.username processor_name,confirmer.username confirmer_name ";
    @Transactional(value="scheduleTransactionManager",readOnly=true)
    public PageResponse<Request> requests(AuthenticatedAccount p,boolean mine,LocalDate from,LocalDate to,String type,String status,int page){Actor a=actor(p);range(from,to,page);Scope scope=requestScope(a,mine);StringBuilder sql=new StringBuilder(REQUEST_FROM).append(scope.sql()).append(" AND s.work_date BETWEEN ? AND ?");var args=new ArrayList<Object>(scope.args());args.add(from);args.add(to);
        if(type!=null&&!type.isBlank()){if(!Set.of("MISSING_CHECKOUT","CONFIRMED_CORRECTION","MISSING_ATTENDANCE","UNCONFIRMED_CORRECTION").contains(type))throw error(HttpStatus.BAD_REQUEST,"Loại yêu cầu không hợp lệ.");sql.append(" AND r.request_type=?");args.add(type);}if(status!=null&&!status.isBlank()){if(status.equals("ACTIVE"))sql.append(" AND r.status IN ('CHO_XU_LY','DANG_XU_LY')");else if(status.equals("HISTORY"))sql.append(" AND r.status IN ('DA_CHAP_NHAN','TU_CHOI')");else if(Set.of("CHO_XU_LY","DANG_XU_LY","DA_CHAP_NHAN","TU_CHOI").contains(status)){sql.append(" AND r.status=?");args.add(status);}else throw error(HttpStatus.BAD_REQUEST,"Trạng thái yêu cầu không hợp lệ.");}
        Long count=db.queryForObject("SELECT COUNT_BIG(*) "+sql,Long.class,args.toArray());long total=count==null?0:count;args.add(page*20);args.add(20);var items=db.query(REQUEST_FIELDS+sql+" ORDER BY r.submitted_at DESC,r.request_id DESC OFFSET ? ROWS FETCH NEXT ? ROWS ONLY",(rs,n)->mapRequest(rs,a),args.toArray());return new PageResponse<>(items,page,20,total,(int)((total+19)/20));}
    @Transactional(value="scheduleTransactionManager",readOnly=true)
    public RequestDetail request(AuthenticatedAccount p,boolean mine,long id){Actor a=actor(p);Scope scope=requestScope(a,mine);var args=new ArrayList<>(scope.args());args.add(id);var list=db.query(REQUEST_FIELDS+REQUEST_FROM+scope.sql()+" AND r.request_id=?",(rs,n)->mapRequest(rs,a),args.toArray());if(list.size()!=1)throw error(HttpStatus.NOT_FOUND,"Yêu cầu không tồn tại trong phạm vi.");Request r=list.get(0);return new RequestDetail(r,detail(p,mine,r.scheduleId()),auditList("attendance_portal_requests",id));}
    @Transactional("scheduleTransactionManager")
    public long submit(AuthenticatedAccount p,Proposal body){Actor a=actor(p);if(body==null)throw error(HttpStatus.BAD_REQUEST,"Thiếu đề xuất.");if(a.role().equals("ADMIN"))throw error(HttpStatus.FORBIDDEN,"ADMIN không gửi yêu cầu công cá nhân.");reason(body.reason());if("MISSING_ATTENDANCE".equals(body.type()))return submitMissing(p,a,body);AttendanceView original=attendance.get(body.attendanceId());if(!Objects.equals(a.employee(),original.employeeId()))throw error(HttpStatus.FORBIDDEN,"Chỉ gửi yêu cầu cho công của mình.");employeeLock(original.employeeId());attendanceLock(original.attendanceId());original=attendance.get(original.attendanceId());if("DA_XAC_NHAN".equals(original.verificationStatus()))throw error(HttpStatus.CONFLICT,"Công đã được xác nhận.");var d=detail(p,true,original.scheduleId());if(!d.row().canRequest())throw error(HttpStatus.CONFLICT,"Công không đủ điều kiện gửi yêu cầu hoặc đã hết hạn.");
        boolean missing="MISSING_CHECKOUT".equals(body.type()),unconfirmed="UNCONFIRMED_CORRECTION".equals(body.type());if(!missing&&!unconfirmed&&!"CONFIRMED_CORRECTION".equals(body.type()))throw error(HttpStatus.BAD_REQUEST,"Loại yêu cầu không hợp lệ.");
        if(missing&&(!"CHUA_XAC_NHAN".equals(original.verificationStatus())||original.actualStart()==null||original.actualEnd()!=null||!Objects.equals(body.actualStart(),original.actualStart())||!"DI_LAM".equals(body.attendanceResult()))||unconfirmed&&!"CHUA_XAC_NHAN".equals(original.verificationStatus())||!missing&&!unconfirmed&&!"DA_XAC_NHAN".equals(original.verificationStatus()))throw error(HttpStatus.CONFLICT,"Loại yêu cầu không khớp trạng thái công.");
        if (original.actualStart()!=null && original.actualEnd()==null && !missing) {
            throw error(HttpStatus.BAD_REQUEST, "Chọn bổ sung check-out và giữ nguyên giờ vào đã ghi nhận.");
        }
        if (original.actualStart()==null && original.actualEnd()!=null
                && (!Objects.equals(body.actualEnd(),original.actualEnd()) || !"DI_LAM".equals(body.attendanceResult()))) {
            throw error(HttpStatus.BAD_REQUEST, "Bổ sung check-in phải giữ nguyên giờ ra đã ghi nhận.");
        }
        if(body.actualEnd()==null&&"DI_LAM".equals(body.attendanceResult()))throw error(HttpStatus.BAD_REQUEST,"Đề xuất đi làm phải có giờ ra.");
        LocalDateTime limit=deadline(!body.type().equals("CONFIRMED_CORRECTION")?d.row().plannedEnd().toLocalDate():original.workDate(),body.type());if(now().isAfter(limit))throw error(HttpStatus.CONFLICT,"Đã hết hạn gửi loại yêu cầu này.");writable(original.workDate());interval(original.employeeId(),original.attendanceId(),body.actualStart(),body.actualEnd(),body.actualBreakMinutes(),body.attendanceResult());
        Long id=db.queryForObject("""
          INSERT dbo.attendance_portal_requests(schedule_id,attendance_id,request_code,request_type,proposed_start,proposed_end,proposed_break_minutes,proposed_result,
           original_version,original_start,original_end,original_break_minutes,original_result,original_verification,original_confirmed_by,reason,submitted_by,submission_deadline)
          OUTPUT INSERTED.request_id SELECT schedule_id,attendance_id,?,?, ?,?,?,?,row_version,actual_start,actual_end,actual_break_minutes,attendance_result,verification_status,confirmed_by,?,?,?
          FROM dbo.attendance_records WHERE attendance_id=? AND invalidated_at IS NULL
          """,Long.class,"ATT-"+UUID.randomUUID().toString().substring(0,18),body.type(),body.actualStart(),body.actualEnd(),body.actualBreakMinutes(),body.attendanceResult(),body.reason().trim(),a.id(),limit,original.attendanceId());
        audit(a.id(),id,"ATTENDANCE_REQUEST_SUBMITTED",body.reason().trim());return id;}
    @Transactional("scheduleTransactionManager")
    public void receive(AuthenticatedAccount p,long id){Actor a=actor(p);RequestDetail d=request(p,false,id);employeeLock(d.current().row().employeeId());requestLock(id);d=request(p,false,id);if(!d.request().canReceive())throw error(HttpStatus.FORBIDDEN,"Không được tiếp nhận yêu cầu này hoặc trạng thái đã thay đổi.");Integer n=db.queryForObject("DECLARE @changed TABLE(id bigint); UPDATE dbo.attendance_portal_requests SET status='DANG_XU_LY',received_by=?,received_at=SYSUTCDATETIME(),processing_deadline=? OUTPUT INSERTED.request_id INTO @changed WHERE request_id=? AND status='CHO_XU_LY'; SELECT COUNT(*) FROM @changed",Integer.class,a.id(),processingDeadline(d.request().workDate()),id);if(n==null||n!=1)throw error(HttpStatus.CONFLICT,"Yêu cầu vừa được tiếp nhận.");audit(a.id(),id,"ATTENDANCE_REQUEST_RECEIVED","Tiếp nhận xử lý");}
    private LocalDateTime processingDeadline(LocalDate workDate){
        LocalDateTime payrollCutoff=workDate.plusMonths(1).withDayOfMonth(5).atTime(23,59,59);
        LocalDateTime sla=deadlinePolicy==null?null:deadlinePolicy.processingDeadline(now().toLocalDate());
        return sla!=null&&sla.isBefore(payrollCutoff)?sla:payrollCutoff;
    }
    private void requestLock(long id){db.queryForObject("SELECT request_id FROM dbo.attendance_portal_requests WITH(UPDLOCK,HOLDLOCK) WHERE request_id=?",Long.class,id);}
    @Transactional("scheduleTransactionManager")
    public void decide(AuthenticatedAccount p,long id,Decision body){Actor a=actor(p);if(body==null)throw error(HttpStatus.BAD_REQUEST,"Thiếu quyết định.");RequestDetail d=request(p,false,id);employeeLock(d.current().row().employeeId());if(d.request().attendanceId()!=null)attendanceLock(d.request().attendanceId());requestLock(id);d=request(p,false,id);Request r=d.request();if(!r.canDecide())throw error(HttpStatus.FORBIDDEN,"Chỉ người tiếp nhận độc lập có quyền hoàn tất yêu cầu đang xử lý.");
        if(!body.approve())reason(body.reason());
        if(body.approve()&&r.requestType().equals("MISSING_ATTENDANCE")){
          approveMissing(p,a,r,body);
        }else if(body.approve()){
          byte[] version=db.queryForObject("SELECT row_version FROM dbo.attendance_records WHERE attendance_id=?",byte[].class,r.attendanceId());byte[] expected=db.queryForObject("SELECT original_version FROM dbo.attendance_portal_requests WHERE request_id=?",byte[].class,id);if(!Arrays.equals(version,expected))throw error(HttpStatus.CONFLICT,"Công gốc đã thay đổi từ lúc gửi yêu cầu; chưa áp dụng đề xuất.");
          writable(r.workDate());validateDecisionTimes(r,body);LocalDateTime end=acceptedEnd(r,body);LocalDateTime acceptedStart=acceptedStart(r,body);
          interval(d.current().row().employeeId(),r.attendanceId(),acceptedStart,end,r.proposedBreakMinutes(),r.proposedResult());
          Long ot=db.queryForObject("SELECT COUNT_BIG(*) FROM dbo.overtime_requests WITH(HOLDLOCK) WHERE employee_id=? AND confirmed_at IS NOT NULL AND requested_start<? AND requested_end>?",Long.class,d.current().row().employeeId(),r.workDate().plusDays(1).atStartOfDay(),r.workDate().atStartOfDay());if(ot==null||ot>0)throw error(HttpStatus.CONFLICT,"Có tăng ca đã xác nhận; cần đối soát riêng trước khi sửa công.");
          String before=snapshot(r.attendanceId());
          if("DA_XAC_NHAN".equals(r.originalVerification())){
            permissions.requireAttendanceAccess(a.id(),d.current().row().employeeId(),r.workDate(),AttendancePermissionService.UNCONFIRM);
            db.update("UPDATE dbo.attendance_records SET verification_status='CHUA_XAC_NHAN',confirmed_by=NULL,confirmed_at=NULL WHERE attendance_id=? AND row_version=?",r.attendanceId(),expected);
            recordWorkflowAudit(a,r.attendanceId(),"ATTENDANCE_UNCONFIRM",r.reason(),before,snapshot(r.attendanceId()));
            expected=db.queryForObject("SELECT row_version FROM dbo.attendance_records WHERE attendance_id=?",byte[].class,r.attendanceId());
          }
          String correctionBefore=snapshot(r.attendanceId());Integer n=db.queryForObject("DECLARE @changed TABLE(id bigint); UPDATE dbo.attendance_records SET actual_start=?,actual_end=?,actual_break_minutes=?,attendance_result=?,correction_reason=?,updated_by=?,updated_at=SYSUTCDATETIME() OUTPUT INSERTED.attendance_id INTO @changed WHERE attendance_id=? AND invalidated_at IS NULL AND row_version=?; SELECT COUNT(*) FROM @changed",Integer.class,acceptedStart,end,r.proposedBreakMinutes(),r.proposedResult(),r.reason(),a.id(),r.attendanceId(),expected);if(n==null||n!=1)throw error(HttpStatus.CONFLICT,"Công bị thay đổi đồng thời.");
          recordWorkflowAudit(a,r.attendanceId(),"ATTENDANCE_CORRECT",r.reason(),correctionBefore,snapshot(r.attendanceId()));
          confirmApproved(r.attendanceId(),a,r.reason());
          saveDecisionTimes(r,body);
          db.update("UPDATE dbo.attendance_portal_requests SET status='DA_CHAP_NHAN',processed_by=?,processed_at=SYSUTCDATETIME(),decision_reason=?,accepted_end=? WHERE request_id=? AND status='DANG_XU_LY'",a.id(),body.reason(),end,id);
        }else db.update("UPDATE dbo.attendance_portal_requests SET status='TU_CHOI',processed_by=?,processed_at=SYSUTCDATETIME(),decision_reason=? WHERE request_id=? AND status='DANG_XU_LY'",a.id(),body.reason().trim(),id);
        audit(a.id(),id,body.approve()?"ATTENDANCE_REQUEST_APPROVED":"ATTENDANCE_REQUEST_REJECTED",body.reason());
    }

    private boolean openSchedule(long scheduleId){
        Long n=db.queryForObject("SELECT COUNT_BIG(*) FROM dbo.attendance_portal_requests WHERE schedule_id=? AND status IN ('CHO_XU_LY','DANG_XU_LY')",Long.class,scheduleId);
        return n==null||n>0;
    }
    private long submitMissing(AuthenticatedAccount p,Actor actor,Proposal body){
        if(body.scheduleId()==null||body.scheduleId()<=0)throw error(HttpStatus.BAD_REQUEST,"Phải chọn lịch làm việc.");
        employeeLock(actor.employee());
        db.queryForObject("SELECT schedule_id FROM dbo.work_schedules WITH(UPDLOCK,HOLDLOCK) WHERE schedule_id=?",Long.class,body.scheduleId());
        Row row=detail(p,true,body.scheduleId()).row();
        if(row.attendanceId()!=null||!row.canRequest())throw error(HttpStatus.CONFLICT,"Lịch đã có công, có yêu cầu đang mở, chưa bắt đầu hoặc đã hết hạn.");
        if(!"DI_LAM".equals(body.attendanceResult())||body.actualStart()==null||body.actualEnd()==null)throw error(HttpStatus.BAD_REQUEST,"Bổ sung công phải nhập đủ giờ vào và giờ ra thực tế.");
        interval(row.employeeId(),0,body.actualStart(),body.actualEnd(),body.actualBreakMinutes(),body.attendanceResult());
        Long id=db.queryForObject("""
          INSERT dbo.attendance_portal_requests(schedule_id,request_code,request_type,proposed_start,proposed_end,proposed_break_minutes,proposed_result,
            original_break_minutes,original_result,original_verification,reason,submitted_by,submission_deadline)
          OUTPUT INSERTED.request_id VALUES(?,?,'MISSING_ATTENDANCE',?,?,?,'DI_LAM',0,'DI_LAM','CHUA_CO_CONG',?,?,?)
          """,Long.class,row.scheduleId(),"ATT-"+UUID.randomUUID().toString().substring(0,18),body.actualStart(),body.actualEnd(),body.actualBreakMinutes(),body.reason().trim(),actor.id(),deadline(row.plannedEnd().toLocalDate(),body.type()));
        audit(actor.id(),id,"ATTENDANCE_REQUEST_SUBMITTED",body.reason().trim());
        return id;
    }
    private void approveMissing(AuthenticatedAccount p,Actor actor,Request request,Decision body){
        Row current=detail(p,false,request.scheduleId()).row();
        if(current.attendanceId()!=null)throw error(HttpStatus.CONFLICT,"Lịch đã phát sinh công sau khi gửi yêu cầu; không ghi đè.");
        validateDecisionTimes(request,body);
        writable(request.workDate());
        interval(current.employeeId(),0,acceptedStart(request,body),acceptedEnd(request,body),request.proposedBreakMinutes(),request.proposedResult());
        AttendanceView created=attendance.createApprovedRequest(new AttendanceInput(current.employeeId(),request.scheduleId(),request.workDate(),acceptedStart(request,body),acceptedEnd(request,body),request.proposedBreakMinutes(),request.proposedResult(),request.reason()),actor.id(),request.requestId());
        db.update("UPDATE dbo.attendance_portal_requests SET attendance_id=?,status='DA_CHAP_NHAN',processed_by=?,processed_at=SYSUTCDATETIME(),decision_reason=?,accepted_end=? WHERE request_id=? AND status='DANG_XU_LY'",created.attendanceId(),actor.id(),body.reason(),acceptedEnd(request,body),request.requestId());
        confirmApproved(created.attendanceId(),actor,request.reason());
        saveDecisionTimes(request,body);
        db.update("INSERT dbo.audit_logs(actor_account_id,action,target_table,target_record_key,reason,after_data_json) VALUES(?,'ATTENDANCE_PORTAL_REQUEST_APPLIED','attendance_records',?,?,?)",actor.id(),Long.toString(created.attendanceId()),request.reason(),snapshot(created.attendanceId()));
    }


    private LocalDateTime acceptedStart(Request r,Decision body){return body.acceptedStart()==null?r.proposedStart():body.acceptedStart();}
    private LocalDateTime acceptedEnd(Request r,Decision body){return body.acceptedEnd()==null?r.proposedEnd():body.acceptedEnd();}
    private void validateDecisionTimes(Request r,Decision body){
        if(!Objects.equals(acceptedStart(r,body),r.proposedStart())||!Objects.equals(acceptedEnd(r,body),r.proposedEnd()))reason(body.adjustmentReason());
        if(r.requestType().equals("MISSING_CHECKOUT")&&!Objects.equals(acceptedStart(r,body),r.originalStart()))throw error(HttpStatus.BAD_REQUEST,"Bổ sung check-out phải giữ nguyên giờ vào.");
        if ("DI_LAM".equals(r.proposedResult()) && r.originalStart()==null && r.originalEnd()!=null
                && !Objects.equals(acceptedEnd(r,body),r.originalEnd())) {
            throw error(HttpStatus.BAD_REQUEST,"Bổ sung check-in phải giữ nguyên giờ ra đã ghi nhận.");
        }
    }
    private void saveDecisionTimes(Request r,Decision body){db.update("UPDATE dbo.attendance_portal_requests SET accepted_start=?,adjustment_reason=? WHERE request_id=?",acceptedStart(r,body),body.adjustmentReason(),r.requestId());}
    private void recordWorkflowAudit(Actor actor,long attendanceId,String action,String why,String before,String after){db.update("INSERT dbo.audit_logs(actor_account_id,action,target_table,target_record_key,reason,before_data_json,after_data_json) VALUES(?,?,'attendance_records',?,?,?,?)",actor.id(),action,Long.toString(attendanceId),why,before,after);if(notifications!=null)notifications.publishRecord(attendanceId,action,actor.id());}
    private void confirmApproved(long id,Actor actor,String why){
        // The request reviewer was authorized and checked for independence before any writes.
        String before=snapshot(id);
        Integer n=db.queryForObject("DECLARE @changed TABLE(id bigint); UPDATE dbo.attendance_records SET verification_status='DA_XAC_NHAN',confirmed_by=?,confirmed_at=SYSUTCDATETIME(),updated_by=?,updated_at=SYSUTCDATETIME(),check_in_reviewed_by=?,check_in_reviewed_at=SYSUTCDATETIME(),check_in_reviewed_value=actual_start,check_out_reviewed_by=?,check_out_reviewed_at=SYSUTCDATETIME(),check_out_reviewed_value=actual_end OUTPUT INSERTED.attendance_id INTO @changed WHERE attendance_id=? AND verification_status='CHUA_XAC_NHAN' AND invalidated_at IS NULL; SELECT COUNT(*) FROM @changed",Integer.class,actor.id(),actor.id(),actor.id(),actor.id(),id);
        if(n==null||n!=1)throw error(HttpStatus.CONFLICT,"Trạng thái công đã thay đổi.");
        recordWorkflowAudit(actor,id,"ATTENDANCE_CONFIRM",why,before,snapshot(id));
    }
    @Transactional("scheduleTransactionManager")
    public AttendanceView markAbsent(AuthenticatedAccount p,long scheduleId,String why){
        Actor actor=actor(p);reason(why);Row row=detail(p,false,scheduleId).row();employeeLock(row.employeeId());row=detail(p,false,scheduleId).row();
        if(!row.canMarkAbsent())throw error(HttpStatus.CONFLICT,"Chỉ ghi nhận vắng sau hạn giải trình và không có yêu cầu đang mở.");
        if(row.attendanceId()!=null){
            attendanceLock(row.attendanceId());writable(row.workDate());String before=snapshot(row.attendanceId());
            db.update("UPDATE dbo.attendance_records SET actual_start=NULL,actual_end=NULL,actual_break_minutes=0,attendance_result='VANG',correction_reason=?,updated_by=?,updated_at=SYSUTCDATETIME(),check_in_reviewed_by=NULL,check_in_reviewed_at=NULL,check_in_reviewed_value=NULL,check_out_reviewed_by=NULL,check_out_reviewed_at=NULL,check_out_reviewed_value=NULL WHERE attendance_id=? AND verification_status='CHUA_XAC_NHAN' AND invalidated_at IS NULL",why.trim(),actor.id(),row.attendanceId());
            recordWorkflowAudit(actor,row.attendanceId(),"ATTENDANCE_CORRECT",why.trim(),before,snapshot(row.attendanceId()));
            return attendance.get(row.attendanceId());
        }
        return attendance.createManual(new AttendanceInput(row.employeeId(),scheduleId,row.workDate(),null,null,0,"VANG",why),actor.id());
    }
    private String snapshot(long id){return db.queryForObject("SELECT (SELECT * FROM dbo.attendance_records WHERE attendance_id=? FOR JSON PATH,WITHOUT_ARRAY_WRAPPER)",String.class,id);}
}

