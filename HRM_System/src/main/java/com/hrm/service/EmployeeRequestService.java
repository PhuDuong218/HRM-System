package com.hrm.service;

import java.math.*;
import java.security.Principal;
import java.time.*;
import java.util.*;
import com.hrm.dto.EmployeeRequestDTOs.*;
import com.hrm.exception.ResourceNotFoundException;
import com.hrm.util.PagingUtils;
import org.springframework.data.domain.*;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/** Request header, subtype, approval, usage and audit always share one transaction. */
@Service
@Transactional(readOnly=true)
public class EmployeeRequestService {
    @org.springframework.beans.factory.annotation.Autowired private PersonnelScopeService personnelScope;
    @org.springframework.beans.factory.annotation.Autowired private EmploymentExitGuard exitGuard;
    @org.springframework.beans.factory.annotation.Autowired private ResignationNoticeAdvice notice;

    @org.springframework.beans.factory.annotation.Autowired private RequestApprovalStages approvalStages;
    @org.springframework.beans.factory.annotation.Autowired private LeaveRequestRules leaveRules;
    private final JdbcTemplate jdbc;
    private final PersonnelAccessService access;
    private final PersonnelWorkflowSupport support;
    private final AssignmentGuardService timeline;
    public EmployeeRequestService(JdbcTemplate jdbc,PersonnelAccessService access,PersonnelWorkflowSupport support,AssignmentGuardService timeline) {
        this.jdbc=jdbc;this.access=access;this.support=support;this.timeline=timeline;
    }
    private static final String SELECT="""
        SELECT r.request_id AS requestId,r.employee_id AS employeeId,r.request_code AS requestCode,
          r.request_type AS requestType,r.reason,CASE WHEN r.status='DA_DUYET' AND r.request_type='NGHI_PHEP' AND w.request_id IS NOT NULL THEN 'DA_HUY' ELSE r.status END AS status,r.processed_by AS processedBy,r.rejection_reason AS rejectionReason,
          CONVERT(varchar(23),r.submitted_at,126)+'Z' AS submittedAt,
          CONVERT(varchar(23),r.processed_at,126)+'Z' AS processedAt,
          l.leave_type_id AS leaveTypeId,CONVERT(varchar(23),l.leave_start,126) AS leaveStart,
          CONVERT(varchar(23),l.leave_end,126) AS leaveEnd,
          CONVERT(varchar(10),d.requested_last_working_date,23) AS requestedLastWorkingDate,
          CONVERT(varchar(10),d.approved_last_working_date,23) AS approvedLastWorkingDate,
          CONVERT(varchar(23),x.executed_at,126)+'Z' AS executedAt,
          CONVERT(varchar(23),w.withdrawn_at,126)+'Z' AS withdrawnAt,w.reason AS withdrawalReason
        FROM dbo.employee_requests r
        LEFT JOIN dbo.leave_request_details l ON l.request_id=r.request_id
        LEFT JOIN dbo.resignation_request_details d ON d.request_id=r.request_id
        LEFT JOIN dbo.g2_resignation_executions x ON x.request_id=r.request_id
        LEFT JOIN dbo.employee_request_withdrawals w ON w.request_id=r.request_id
        """;
    private Map<String,Object> required(long id) {
        var rows=jdbc.queryForList(SELECT+" WHERE r.request_id=? AND r.request_type IN ('NGHI_PHEP','NGHI_VIEC')",id);
        if(rows.isEmpty()) throw new ResourceNotFoundException("Không tìm thấy đơn: "+id);
        return rows.get(0);
    }
    public Map<String,Object> detail(long id,Principal p) {
        var actor=access.actor(p);
        var row=required(id);
        long employee=number(row,"employeeId");
        if(actor.employeeId()!=employee && (approvalStages==null||!approvalStages.canRead(employee,p))){
            access.manager(p);
            if("HR".equals(actor.role())) personnelScope.managedEmployee(employee);
        }
        if(approvalStages!=null)row.put("approvalStage",approvalStages.stage(id));
        if(leaveRules!=null)row.put("evidenceReference",leaveRules.evidence(id));
        if(notice!=null&&"NGHI_VIEC".equals(row.get("requestType")))row.put("noticeWarning",notice.warning(number(row,"employeeId"),Instant.parse(row.get("submittedAt").toString()).atZone(ZoneId.of("Asia/Ho_Chi_Minh")).toLocalDate(),LocalDate.parse(row.get("requestedLastWorkingDate").toString())));
        if(jdbc.queryForObject("SELECT CASE WHEN OBJECT_ID('dbo.personnel_termination_decisions') IS NULL THEN 0 ELSE 1 END",Integer.class)==1){var decisions=jdbc.queryForList("SELECT decision_type AS decisionType,decision_number AS decisionNumber FROM dbo.personnel_termination_decisions WHERE request_id=?",id);if(!decisions.isEmpty())row.putAll(decisions.get(0));}
        row.put("allocations",jdbc.queryForList("SELECT schedule_id AS scheduleId,entitlement_id AS entitlementId,charged_hours AS chargedHours FROM dbo.g2_leave_allocations WHERE request_id=? ORDER BY allocation_id",id));
        return row;
    }
    public Page<Map<String,Object>> list(boolean mine,String status,String type,int page,int size,Principal p) {
        return list(mine,status,type,page,size,p,null);
    }
    public Page<Map<String,Object>> list(boolean mine,String status,String type,int page,int size,Principal p,Long employeeId) {
        var actor=mine?access.actor(p):access.manager(p);
        if(status!=null&&!Set.of("CHO_DUYET","DA_DUYET","TU_CHOI","DA_HUY").contains(status)) throw PersonnelWorkflowSupport.bad("Trạng thái không hợp lệ");
        if(type!=null&&!Set.of("NGHI_PHEP","NGHI_VIEC").contains(type)) throw PersonnelWorkflowSupport.bad("Loại đơn không hợp lệ");
        var pageable=PagingUtils.create(page,size,Sort.unsorted());
        String where=" WHERE r.request_type IN ('NGHI_PHEP','NGHI_VIEC') AND (?=0 OR r.employee_id=?) AND (? IS NULL OR (CASE WHEN r.status='DA_DUYET' AND r.request_type='NGHI_PHEP' AND w.request_id IS NOT NULL THEN 'DA_HUY' ELSE r.status END)=?) AND (? IS NULL OR r.request_type=?) AND (?=1 OR w.request_id IS NULL)";
        if(!mine && PersonnelScopeService.isHr()) where+=" AND "+personnelScope.sqlEmployees("r.employee_id");
        var args=new ArrayList<Object>(Arrays.asList(mine?1:0,actor.employeeId(),status,status,type,type,(mine||"DA_HUY".equals(status))?1:0));
        if(!mine && employeeId!=null){where+=" AND r.employee_id=?";args.add(employeeId);}
        long total=jdbc.queryForObject("SELECT COUNT_BIG(*) FROM dbo.employee_requests r LEFT JOIN dbo.employee_request_withdrawals w ON w.request_id=r.request_id"+where,Long.class,args.toArray());
        args.add(pageable.getOffset());args.add(pageable.getPageSize());
        var rows=jdbc.queryForList(SELECT+where+" ORDER BY r.submitted_at DESC,r.request_id DESC OFFSET ? ROWS FETCH NEXT ? ROWS ONLY",args.toArray());
        return new PageImpl<>(rows,pageable,total);
    }
    private long header(long employee,String type,String reason,long actor) {
        String code="REQ-"+UUID.randomUUID();
        jdbc.update("INSERT dbo.employee_requests(employee_id,request_code,request_type,reason,submitted_at,created_by) VALUES(?,?,?,?,?,?)",
            employee,code,type,reason.trim(),support.utc(),actor);
        long id=jdbc.queryForObject("SELECT request_id FROM dbo.employee_requests WHERE request_code=?",Long.class,code);
        if(approvalStages!=null)approvalStages.register(id,employee);
        return id;
    }
    @Transactional
    public Map<String,Object> leave(Leave input,Principal p) {
        var actor=access.actor(p);support.lock();timeline.lockTimeline();
        lockEmployee(actor.employeeId());
        validateLeave(actor.employeeId(),input.leaveTypeId(),input.leaveStart(),input.leaveEnd(),null);
        if(leaveRules!=null)leaveRules.eligibility(actor.employeeId(),input.leaveTypeId(),input.evidenceReference(),input.leaveStart().toLocalDate());
        long id=header(actor.employeeId(),"NGHI_PHEP",input.reason(),actor.accountId());
        if(leaveRules!=null)leaveRules.evidence(id,input.evidenceReference());
        jdbc.update("INSERT dbo.leave_request_details(request_id,leave_type_id,leave_start,leave_end,created_by) VALUES(?,?,?,?,?)",
            id,input.leaveTypeId(),input.leaveStart(),input.leaveEnd(),actor.accountId());
        var result=required(id);support.audit(actor.accountId(),"LEAVE_REQUEST_CREATE","employee_requests",id,input.reason(),null,result);return result;
    }
    @Transactional
    public Map<String,Object> resign(Resignation input,Principal p) {
        var actor=access.actor(p);support.lock();timeline.lockTimeline();
        lockEmployee(actor.employeeId());
        if(input.requestedLastWorkingDate().isBefore(support.today())) throw PersonnelWorkflowSupport.bad("Ngày làm cuối không được ở quá khứ");
        long duplicate=jdbc.queryForObject("""
            SELECT COUNT_BIG(*) FROM dbo.employee_requests r LEFT JOIN dbo.g2_resignation_executions x ON x.request_id=r.request_id
            WHERE r.employee_id=? AND r.request_type='NGHI_VIEC' AND (r.status='CHO_DUYET' OR (r.status='DA_DUYET' AND x.request_id IS NULL))
              AND NOT EXISTS(SELECT 1 FROM dbo.employee_request_withdrawals w WHERE w.request_id=r.request_id)
            """,Long.class,actor.employeeId());
        if(duplicate>0) throw PersonnelWorkflowSupport.conflict("Đã có đơn nghỉ việc chờ duyệt hoặc chờ hiệu lực");
        long id=header(actor.employeeId(),"NGHI_VIEC",input.reason(),actor.accountId());
        jdbc.update("INSERT dbo.resignation_request_details(request_id,requested_last_working_date,created_by) VALUES(?,?,?)",id,input.requestedLastWorkingDate(),actor.accountId());
        var result=required(id);support.audit(actor.accountId(),"RESIGNATION_REQUEST_CREATE","employee_requests",id,input.reason(),null,result);return result;
    }
    private void lockEmployee(long employee) {
        var rows=jdbc.queryForList("SELECT employment_status FROM dbo.employees WITH(UPDLOCK,HOLDLOCK) WHERE employee_id=?",String.class,employee);
        if(rows.isEmpty()) throw new ResourceNotFoundException("Không tìm thấy nhân viên");
        if(!"DANG_LAM".equals(rows.get(0))) throw PersonnelWorkflowSupport.conflict("Nhân viên đã nghỉ việc");
    }
    private void pending(Map<String,Object> row,PersonnelAccessService.Actor actor) {
        long employee=number(row,"employeeId");
        if("HR".equals(actor.role())) personnelScope.managedEmployee(employee);
        if(employee==actor.employeeId()) throw new ResponseStatusException(HttpStatus.FORBIDDEN,"Không được tự duyệt/từ chối đơn");
        if(!"CHO_DUYET".equals(row.get("status"))||row.get("withdrawnAt")!=null) throw PersonnelWorkflowSupport.conflict("Đơn đã được xử lý hoặc đã rút");
    }
    @Transactional
    public Map<String,Object> withdraw(long id,Rejection input,Principal p) {
        var actor=access.actor(p);support.lock();var before=required(id);
        if(actor.employeeId()!=number(before,"employeeId"))throw new ResponseStatusException(HttpStatus.FORBIDDEN,"Chỉ người nộp được rút đơn của mình");
        if(!"CHO_DUYET".equals(before.get("status"))||before.get("withdrawnAt")!=null)throw PersonnelWorkflowSupport.conflict("Chỉ được rút đơn đang chờ và chưa rút");
        jdbc.update("INSERT dbo.employee_request_withdrawals(request_id,withdrawn_by,withdrawn_at,reason) VALUES(?,?,?,?)",id,actor.accountId(),support.utc(),input.reason().trim());
        var after=required(id);support.audit(actor.accountId(),"REQUEST_WITHDRAW","employee_requests",id,input.reason(),before,after);return after;
    }
    @Transactional
    public Map<String,Object> reject(long id,Rejection input,Principal p) {
        var actor=access.approver(p);support.lock();var before=required(id);pending(before,actor);
        process(id,"TU_CHOI",actor.accountId(),input.reason().trim());
        var after=required(id);support.audit(actor.accountId(),"REQUEST_REJECT","employee_requests",id,input.reason(),before,after);return after;
    }
    @Transactional
    public Map<String,Object> approve(long id,Decision input,Principal p) {
        var actor=access.approver(p);support.lock();timeline.lockTimeline();
        var before=required(id);pending(before,actor);long employee=number(before,"employeeId");
        if(approvalStages!=null)approvalStages.requireFinalApproval(id,employee,actor.accountId());
        lockEmployee(employee);
        if("NGHI_PHEP".equals(before.get("requestType"))) {
            if(input.approvedLastWorkingDate()!=null) throw PersonnelWorkflowSupport.bad("Đơn phép không có ngày nghỉ việc");
            long type=number(before,"leaveTypeId");
            var start=LocalDateTime.parse(before.get("leaveStart").toString());var end=LocalDateTime.parse(before.get("leaveEnd").toString());
            if(leaveRules!=null) leaveRules.eligibility(employee,type,leaveRules.evidence(id),start.toLocalDate());
            var charges=validateLeave(employee,type,start,end,id);
            for(var c:charges) jdbc.update("INSERT dbo.g2_leave_allocations(request_id,schedule_id,entitlement_id,charged_hours) VALUES(?,?,?,?)",
                id,c.schedule(),c.entitlement(),c.hours());
        } else {
            LocalDate last=input.approvedLastWorkingDate()==null?LocalDate.parse(before.get("requestedLastWorkingDate").toString()):input.approvedLastWorkingDate();
            if(last.isBefore(support.today())) throw PersonnelWorkflowSupport.bad("Ngày làm cuối được duyệt không được ở quá khứ");
            checkResignation(employee,last);
            jdbc.update("UPDATE dbo.resignation_request_details SET approved_last_working_date=?,updated_at=?,updated_by=? WHERE request_id=?",last,support.utc(),actor.accountId(),id);
        }
        process(id,"DA_DUYET",actor.accountId(),null);
        var after=required(id);support.audit(actor.accountId(),"REQUEST_APPROVE","employee_requests",id,input.note(),before,after);return after;
    }
    public com.hrm.dto.ResignationReadinessDTO resignationReadiness(long id,LocalDate proposed,Principal p){
        var actor=access.approver(p);var row=required(id);long employee=number(row,"employeeId");
        personnelScope.managedEmployee(employee);
        if(actor.employeeId()==employee)throw new ResponseStatusException(HttpStatus.FORBIDDEN,"Không được tự duyệt đơn nghỉ việc");
        if(!"NGHI_VIEC".equals(row.get("requestType")))throw PersonnelWorkflowSupport.bad("Chỉ kiểm tra bàn giao cho đơn nghỉ việc");
        LocalDate last=proposed!=null?proposed:LocalDate.parse(Objects.toString(row.get("approvedLastWorkingDate"),row.get("requestedLastWorkingDate").toString()));
        if(last.isBefore(support.today()))throw PersonnelWorkflowSupport.bad("Ngày làm cuối được duyệt không được ở quá khứ");
        var person=jdbc.queryForMap("SELECT employee_code,full_name,employment_status FROM dbo.employees WHERE employee_id=?",employee);
        String blocker=approvalStages==null?null:approvalStages.finalApprovalBlocker(id,employee);
        if(!"CHO_DUYET".equals(row.get("status"))||row.get("withdrawnAt")!=null)blocker="Đơn không còn ở trạng thái chờ duyệt cuối";
        if(!"DANG_LAM".equals(person.get("employment_status")))blocker="Nhân viên đã nghỉ việc";
        var issues=exitGuard.inspect(employee,last);
        boolean ready=blocker==null&&issues.isEmpty();
        return new com.hrm.dto.ResignationReadinessDTO(id,employee,person.get("employee_code").toString(),person.get("full_name").toString(),last,ready,
            blocker!=null?blocker:ready?"Đã đủ điều kiện kiểm tra để HR duyệt cuối":"Cần xử lý các dữ liệu bên dưới trước khi duyệt cuối",issues);
    }
    @Transactional
    public Map<String,Object> cancelApproved(long id,Rejection input,Principal p){
        var actor=access.approver(p);support.lock();timeline.lockTimeline();var before=required(id);
        long employee=number(before,"employeeId");personnelScope.employee(employee,false);
        if(!"NGHI_PHEP".equals(before.get("requestType"))||!"DA_DUYET".equals(before.get("status"))||before.get("withdrawnAt")!=null)throw PersonnelWorkflowSupport.conflict("Chỉ được hủy đơn nghỉ phép đã duyệt và chưa hủy");
        var start=LocalDateTime.parse(before.get("leaveStart").toString());var end=LocalDateTime.parse(before.get("leaveEnd").toString());
        if(!start.isAfter(support.localNow()))throw PersonnelWorkflowSupport.conflict("Đơn đã bắt đầu; cần quy trình điều chỉnh riêng");
        leaveRules.requireMutable(employee,start,end);
        jdbc.update("INSERT dbo.employee_request_withdrawals(request_id,withdrawn_by,withdrawn_at,reason) VALUES(?,?,?,?)",id,actor.accountId(),support.utc(),input.reason().trim());
        var after=required(id);support.audit(actor.accountId(),"LEAVE_APPROVAL_CANCEL","employee_requests",id,input.reason(),before,after);return after;
    }
    private void process(long id,String status,long actor,String rejection) {
        int updated=jdbc.queryForObject("""
            SET NOCOUNT ON;
            DECLARE @changed TABLE(request_id bigint);
            UPDATE dbo.employee_requests SET status=?,processed_by=?,processed_at=?,rejection_reason=?,updated_at=?,updated_by=?
            OUTPUT inserted.request_id INTO @changed
            WHERE request_id=? AND status='CHO_DUYET';
            SELECT COUNT(*) FROM @changed;
            SET NOCOUNT OFF;
            """,Integer.class,status,actor,support.utc(),rejection,support.utc(),actor,id);
        if(updated!=1) throw PersonnelWorkflowSupport.conflict("Đơn đã được xử lý bởi giao dịch khác");
    }
    private record Charge(long schedule,Long entitlement,BigDecimal hours) {}
    private List<Charge> validateLeave(long employee,long type,LocalDateTime start,LocalDateTime end,Long ignore) {
        if(leaveRules!=null){
            LocalDateTime submitted=ignore==null?null:LocalDateTime.ofInstant(Instant.parse(required(ignore).get("submittedAt").toString()),ZoneId.of("Asia/Ho_Chi_Minh"));
            leaveRules.validateTime(employee,type,start,end,submitted);
        }else if(start==null||end==null||!start.isBefore(end)||start.isBefore(support.localNow())) throw PersonnelWorkflowSupport.bad("Thời gian nghỉ phải ở tương lai và kết thúc sau bắt đầu");
        var policies=jdbc.queryForList("""
            SELECT p.consumes_entitlement FROM dbo.leave_types t JOIN dbo.g2_leave_policies p ON p.leave_type_id=t.leave_type_id
            WHERE t.leave_type_id=? AND t.is_active=1
            """,Boolean.class,type);
        if(policies.size()!=1) throw PersonnelWorkflowSupport.conflict("Loại nghỉ chưa hoạt động hoặc ADMIN chưa cấu hình chính sách trừ hạn mức");
        long overlap=jdbc.queryForObject("""
            SELECT COUNT_BIG(*) FROM dbo.employee_requests r JOIN dbo.leave_request_details l ON l.request_id=r.request_id
            WHERE r.employee_id=? AND r.status IN ('CHO_DUYET','DA_DUYET') AND l.leave_start<? AND l.leave_end>?
              AND (? IS NULL OR r.request_id<>?)
              AND NOT EXISTS(SELECT 1 FROM dbo.employee_request_withdrawals w WHERE w.request_id=r.request_id)
            """,Long.class,employee,end,start,ignore,ignore);
        if(overlap>0) throw PersonnelWorkflowSupport.conflict("Trùng thời gian với đơn đang chờ hoặc đã duyệt");
        var resigned=jdbc.queryForList("""
            SELECT d.approved_last_working_date FROM dbo.employee_requests r JOIN dbo.resignation_request_details d ON d.request_id=r.request_id
            WHERE r.employee_id=? AND r.status='DA_DUYET' AND NOT EXISTS(SELECT 1 FROM dbo.g2_resignation_executions done WHERE done.request_id=r.request_id) AND NOT EXISTS(SELECT 1 FROM dbo.employee_request_withdrawals cw WHERE cw.request_id=r.request_id) AND d.approved_last_working_date<?
            """,LocalDate.class,employee,end.minusNanos(1).toLocalDate());
        if(!resigned.isEmpty()) throw PersonnelWorkflowSupport.conflict("Thời gian nghỉ vượt ngày làm cuối đã duyệt");
        var schedules=jdbc.queryForList("""
            SELECT schedule_id,work_date,planned_start,planned_end,planned_break_minutes FROM dbo.work_schedules WITH(UPDLOCK,HOLDLOCK)
            WHERE cancelled_at IS NULL AND employee_id=? AND planned_start<? AND planned_end>? ORDER BY planned_start,schedule_id
            """,employee,end,start);
        if(schedules.isEmpty()) throw PersonnelWorkflowSupport.conflict("Chưa có lịch làm việc trong khoảng nghỉ");
        var charges=new ArrayList<Charge>();var totals=new HashMap<Long,BigDecimal>();LocalDateTime previousEnd=null;
        for(var row:schedules) {
            var a=((java.sql.Timestamp)row.get("planned_start")).toLocalDateTime();var b=((java.sql.Timestamp)row.get("planned_end")).toLocalDateTime();
            if(previousEnd!=null&&a.isBefore(previousEnd)) throw PersonnelWorkflowSupport.conflict("Lịch làm việc bị chồng thời gian, cần đối soát");previousEnd=b;
            var from=start.isAfter(a)?start:a;var to=end.isBefore(b)?end:b;
            int pause=((Number)row.get("planned_break_minutes")).intValue();
            if(leaveRules==null&&pause>0&&(from.isAfter(a)||to.isBefore(b))) throw PersonnelWorkflowSupport.conflict("Nghỉ một phần ca có giờ nghỉ giữa ca chưa xác định vị trí; chọn trọn ca hoặc chia lịch chính xác");
            long millis=leaveRules==null?Duration.between(from,to).toMillis()-pause*60000L:leaveRules.netMillis(((Number)row.get("schedule_id")).longValue(),a,b,from,to,pause);
            if(millis<=0) continue;
            BigDecimal hours=BigDecimal.valueOf(millis).divide(BigDecimal.valueOf(3600000),4,RoundingMode.HALF_UP);
            Long entitlement=null;
            if(policies.get(0)) {
                var date=((java.sql.Date)row.get("work_date")).toLocalDate();
                var limits=jdbc.queryForList("SELECT entitlement_id FROM dbo.leave_entitlements WITH(UPDLOCK,HOLDLOCK) WHERE employee_id=? AND leave_type_id=? AND period_start<=? AND period_end>?",Long.class,employee,type,date,date);
                if(limits.size()!=1) throw PersonnelWorkflowSupport.conflict("Phải có đúng một hạn mức phủ ngày làm việc "+date);
                entitlement=limits.get(0);totals.merge(entitlement,hours,BigDecimal::add);
            }
            charges.add(new Charge(((Number)row.get("schedule_id")).longValue(),entitlement,hours));
        }
        if(charges.isEmpty()) throw PersonnelWorkflowSupport.conflict("Khoảng nghỉ không có giờ làm việc có thể tính");
        if(leaveRules!=null){var maximum=leaveRules.get(type).maximumHours();if(maximum!=null&&charges.stream().map(Charge::hours).reduce(BigDecimal.ZERO,BigDecimal::add).compareTo(maximum)>0)throw PersonnelWorkflowSupport.conflict("Số giờ nghỉ vượt hạn mức mỗi đơn của loại nghỉ");}
        for(var entry:totals.entrySet()) {
            BigDecimal granted=jdbc.queryForObject("SELECT entitled_hours FROM dbo.leave_entitlements WHERE entitlement_id=?",BigDecimal.class,entry.getKey());
            if(used(entry.getKey()).add(entry.getValue()).compareTo(granted)>0) throw PersonnelWorkflowSupport.conflict("Không đủ số giờ phép còn lại");
        }
        return charges;
    }
    public BigDecimal used(long entitlement) {
        return jdbc.queryForObject("""
            SELECT COALESCE(SUM(a.charged_hours),0) FROM dbo.g2_leave_allocations a JOIN dbo.employee_requests r ON r.request_id=a.request_id
            WHERE a.entitlement_id=? AND r.status='DA_DUYET' AND NOT EXISTS(SELECT 1 FROM dbo.employee_request_withdrawals cw WHERE cw.request_id=r.request_id)
            """,BigDecimal.class,entitlement);
    }
    public List<Map<String,Object>> balances(long employee,Principal p) {
        access.ownerOrManager(p,employee);
        personnelScope.employee(employee,true);
        return jdbc.queryForList("""
            SELECT e.entitlement_id AS entitlementId,e.leave_type_id AS leaveTypeId,
              CONVERT(varchar(10),e.period_start,23) AS periodStart,CONVERT(varchar(10),e.period_end,23) AS periodEnd,
              e.entitled_hours AS entitledHours,COALESCE(u.hours,0) AS usedHours,e.entitled_hours-COALESCE(u.hours,0) AS remainingHours
            FROM dbo.leave_entitlements e OUTER APPLY (
              SELECT SUM(a.charged_hours) AS hours FROM dbo.g2_leave_allocations a JOIN dbo.employee_requests r ON r.request_id=a.request_id
              WHERE a.entitlement_id=e.entitlement_id AND r.status='DA_DUYET' AND NOT EXISTS(SELECT 1 FROM dbo.employee_request_withdrawals cw WHERE cw.request_id=r.request_id)) u
            WHERE e.employee_id=? ORDER BY e.period_start DESC,e.entitlement_id DESC
            """,employee);
    }
    private void checkResignation(long employee,LocalDate last) { exitGuard.check(employee,last); }
    @Transactional
    public Map<String,Object> directTermination(long employee,com.hrm.dto.DirectTerminationRequest input,Principal p){
        var actor=access.manager(p);personnelScope.employee(employee,false);
        if(actor.employeeId()==employee)throw new ResponseStatusException(HttpStatus.FORBIDDEN,"Không được tự ra quyết định thôi việc");
        support.lock();timeline.lockTimeline();lockEmployee(employee);
        if(jdbc.queryForObject("SELECT CASE WHEN OBJECT_ID('dbo.personnel_termination_decisions') IS NULL THEN 0 ELSE 1 END",Integer.class)!=1)throw PersonnelWorkflowSupport.conflict("Cần migration 008 để lưu quyết định thôi việc");
        if(input.lastWorkingDate()==null||input.reason()==null||input.reason().isBlank()||input.decisionType()==null||input.decisionNumber()==null||input.decisionNumber().isBlank())throw PersonnelWorkflowSupport.bad("Cần lý do, ngày làm cuối, loại và số quyết định");
        checkResignation(employee,input.lastWorkingDate());
        long pending=jdbc.queryForObject("SELECT COUNT_BIG(*) FROM dbo.employee_requests r LEFT JOIN dbo.g2_resignation_executions x ON x.request_id=r.request_id WHERE r.employee_id=? AND r.request_type='NGHI_VIEC' AND (r.status='CHO_DUYET' OR (r.status='DA_DUYET' AND x.request_id IS NULL)) AND NOT EXISTS(SELECT 1 FROM dbo.employee_request_withdrawals w WHERE w.request_id=r.request_id)",Long.class,employee);
        if(pending>0)throw PersonnelWorkflowSupport.conflict("Đã có đơn/quyết định nghỉ việc chưa hoàn tất; cần xử lý trước");
        String code="EXIT-"+UUID.randomUUID();
        jdbc.update("INSERT dbo.employee_requests(employee_id,request_code,request_type,reason,submitted_at,created_by) VALUES(?,?,'NGHI_VIEC',?,?,?)",employee,code,input.reason().trim(),support.utc(),actor.accountId());
        long id=jdbc.queryForObject("SELECT request_id FROM dbo.employee_requests WHERE request_code=?",Long.class,code);
        jdbc.update("INSERT dbo.resignation_request_details(request_id,requested_last_working_date,approved_last_working_date,created_by) VALUES(?,?,?,?)",id,input.lastWorkingDate(),input.lastWorkingDate(),actor.accountId());
        process(id,"DA_DUYET",actor.accountId(),null);
        jdbc.update("INSERT dbo.personnel_termination_decisions(request_id,decision_type,decision_number,created_by) VALUES(?,?,?,?)",id,input.decisionType().name(),input.decisionNumber().trim(),actor.accountId());
        support.audit(actor.accountId(),"EMPLOYEE_TERMINATION_DECISION","employee_requests",id,input.reason(),null,input);
        if(input.lastWorkingDate().isBefore(support.today()))execute(id,actor.accountId());
        return required(id);
    }
    /** Invoked after the approved last working day, never at approval time. */
    @Transactional
    public Map<String,Object> execute(long id,Long actor) {
        support.lock();timeline.lockTimeline();var before=required(id);
        if(!"NGHI_VIEC".equals(before.get("requestType"))||!"DA_DUYET".equals(before.get("status"))) throw PersonnelWorkflowSupport.conflict("Chỉ thực thi đơn nghỉ việc đã duyệt");
        if(before.get("executedAt")!=null) return before;
        LocalDate last=LocalDate.parse(before.get("approvedLastWorkingDate").toString());
        if(!last.isBefore(support.today())) throw PersonnelWorkflowSupport.conflict("Chưa qua ngày làm cuối được duyệt");
        long employee=number(before,"employeeId");lockEmployee(employee);checkResignation(employee,last);
        var rows=jdbc.queryForList("""
            SELECT assignment_id FROM dbo.employee_assignments WHERE employee_id=? AND effective_from<=?
              AND (effective_to IS NULL OR effective_to>?)
            """,Long.class,employee,last,last);
        if(rows.size()!=1) throw PersonnelWorkflowSupport.conflict("Phải có đúng một phân công vào ngày làm cuối");
        jdbc.update("UPDATE dbo.employee_assignments SET effective_to=?,updated_at=?,updated_by=? WHERE assignment_id=?",last.plusDays(1),support.utc(),actor,rows.get(0));
        jdbc.update("UPDATE dbo.employees SET employment_status='DA_NGHI',termination_date=?,updated_at=?,updated_by=? WHERE employee_id=?",last,support.utc(),actor,employee);
        // Match direct termination: reopening an account must never revive an old JWT.
        jdbc.update("UPDATE dbo.accounts SET is_active=0,token_version=COALESCE(token_version,0)+1,updated_at=?,updated_by=? WHERE employee_id=?",support.utc(),actor,employee);
        jdbc.update("INSERT dbo.g2_resignation_executions(request_id,last_working_date,executed_at) VALUES(?,?,?)",id,last,support.utc());
        var after=required(id);support.audit(actor,"RESIGNATION_EXECUTE","employee_requests",id,"Áp dụng ngày làm cuối đã duyệt",before,after);return after;
    }
    public List<Long> due(long afterId) {
        return jdbc.queryForList("""
            SELECT TOP(100) r.request_id FROM dbo.employee_requests r JOIN dbo.resignation_request_details d ON d.request_id=r.request_id
            LEFT JOIN dbo.g2_resignation_executions x ON x.request_id=r.request_id
            WHERE r.status='DA_DUYET' AND d.approved_last_working_date<? AND x.request_id IS NULL AND r.request_id>?
            ORDER BY r.request_id
            """,Long.class,support.today(),afterId);
    }
    @Transactional
    public void policy(long type,Policy input,Principal p) {
        var actor=access.admin(p);support.lock();
        if(jdbc.queryForObject("SELECT COUNT(*) FROM dbo.leave_types WHERE leave_type_id=?",Integer.class,type)==0) throw new ResourceNotFoundException("Không tìm thấy loại nghỉ");
        var before=jdbc.queryForList("SELECT consumes_entitlement FROM dbo.g2_leave_policies WHERE leave_type_id=?",Boolean.class,type);
        if(before.size()==1&&before.get(0).equals(input.consumesEntitlement())) return;
        if(jdbc.queryForObject("SELECT COUNT(*) FROM dbo.leave_request_details WHERE leave_type_id=?",Integer.class,type)>0)
            throw PersonnelWorkflowSupport.conflict("Loại nghỉ đã có đơn; tạo loại mới khi đổi chính sách");
        jdbc.update("""
            UPDATE dbo.g2_leave_policies SET consumes_entitlement=?,updated_at=?,updated_by=? WHERE leave_type_id=?;
            IF @@ROWCOUNT=0 INSERT dbo.g2_leave_policies(leave_type_id,consumes_entitlement,updated_at,updated_by) VALUES(?,?,?,?);
            """,input.consumesEntitlement(),support.utc(),actor.accountId(),type,type,input.consumesEntitlement(),support.utc(),actor.accountId());
        support.audit(actor.accountId(),"LEAVE_POLICY_SET","leave_types",type,null,before,input);
    }
    public Map<String,Object> policy(long type,Principal p) {
        access.manager(p);var rows=jdbc.queryForList("SELECT leave_type_id AS leaveTypeId,consumes_entitlement AS consumesEntitlement FROM dbo.g2_leave_policies WHERE leave_type_id=?",type);
        if(rows.isEmpty()) throw new ResourceNotFoundException("Chưa cấu hình chính sách loại nghỉ");return rows.get(0);
    }
    public static long number(Map<String,Object> row,String key){return ((Number)row.get(key)).longValue();}
}
