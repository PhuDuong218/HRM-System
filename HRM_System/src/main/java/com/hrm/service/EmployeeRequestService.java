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
    private final JdbcTemplate jdbc;
    private final PersonnelAccessService access;
    private final PersonnelWorkflowSupport support;
    private final AssignmentGuardService timeline;
    public EmployeeRequestService(JdbcTemplate jdbc,PersonnelAccessService access,PersonnelWorkflowSupport support,AssignmentGuardService timeline) {
        this.jdbc=jdbc;this.access=access;this.support=support;this.timeline=timeline;
    }
    private static final String SELECT="""
        SELECT r.request_id AS requestId,r.employee_id AS employeeId,r.request_code AS requestCode,
          r.request_type AS requestType,r.reason,r.status,r.processed_by AS processedBy,r.rejection_reason AS rejectionReason,
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
        var rows=jdbc.queryForList(SELECT+" WHERE r.request_id=?",id);
        if(rows.isEmpty()) throw new ResourceNotFoundException("Không tìm thấy đơn: "+id);
        return rows.get(0);
    }
    public Map<String,Object> detail(long id,Principal p) {
        access.actor(p);var row=required(id);access.ownerOrManager(p,number(row,"employeeId"));
        row.put("allocations",jdbc.queryForList("SELECT schedule_id AS scheduleId,entitlement_id AS entitlementId,charged_hours AS chargedHours FROM dbo.g2_leave_allocations WHERE request_id=? ORDER BY allocation_id",id));
        return row;
    }
    public Page<Map<String,Object>> list(boolean mine,String status,String type,int page,int size,Principal p) {
        var actor=mine?access.actor(p):access.manager(p);
        if(status!=null&&!Set.of("CHO_DUYET","DA_DUYET","TU_CHOI").contains(status)) throw PersonnelWorkflowSupport.bad("Trạng thái không hợp lệ");
        if(type!=null&&!Set.of("NGHI_PHEP","NGHI_VIEC").contains(type)) throw PersonnelWorkflowSupport.bad("Loại đơn không hợp lệ");
        var pageable=PagingUtils.create(page,size,Sort.unsorted());
        String where=" WHERE (?=0 OR r.employee_id=?) AND (? IS NULL OR r.status=?) AND (? IS NULL OR r.request_type=?) AND (?=1 OR w.request_id IS NULL)";
        Object[] params={mine?1:0,actor.employeeId(),status,status,type,type,mine?1:0};
        long total=jdbc.queryForObject("SELECT COUNT_BIG(*) FROM dbo.employee_requests r LEFT JOIN dbo.employee_request_withdrawals w ON w.request_id=r.request_id"+where,Long.class,params);
        var args=new ArrayList<Object>(Arrays.asList(params));args.add((long)page*size);args.add(size);
        var rows=jdbc.queryForList(SELECT+where+" ORDER BY r.submitted_at DESC,r.request_id DESC OFFSET ? ROWS FETCH NEXT ? ROWS ONLY",args.toArray());
        return new PageImpl<>(rows,pageable,total);
    }
    private long header(long employee,String type,String reason,long actor) {
        String code="REQ-"+UUID.randomUUID();
        jdbc.update("INSERT dbo.employee_requests(employee_id,request_code,request_type,reason,submitted_at,created_by) VALUES(?,?,?,?,?,?)",
            employee,code,type,reason.trim(),support.utc(),actor);
        return jdbc.queryForObject("SELECT request_id FROM dbo.employee_requests WHERE request_code=?",Long.class,code);
    }
    @Transactional
    public Map<String,Object> leave(Leave input,Principal p) {
        var actor=access.actor(p);support.lock();timeline.lockTimeline();
        lockEmployee(actor.employeeId());
        validateLeave(actor.employeeId(),input.leaveTypeId(),input.leaveStart(),input.leaveEnd(),null);
        long id=header(actor.employeeId(),"NGHI_PHEP",input.reason(),actor.accountId());
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
        if(number(row,"employeeId")==actor.employeeId()) throw new ResponseStatusException(HttpStatus.FORBIDDEN,"Không được tự duyệt/từ chối đơn");
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
        var before=required(id);pending(before,actor);long employee=number(before,"employeeId");lockEmployee(employee);
        if("NGHI_PHEP".equals(before.get("requestType"))) {
            if(input.approvedLastWorkingDate()!=null) throw PersonnelWorkflowSupport.bad("Đơn phép không có ngày nghỉ việc");
            long type=number(before,"leaveTypeId");
            var start=LocalDateTime.parse(before.get("leaveStart").toString());var end=LocalDateTime.parse(before.get("leaveEnd").toString());
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
        if(start==null||end==null||!start.isBefore(end)||start.isBefore(support.localNow())) throw PersonnelWorkflowSupport.bad("Thời gian nghỉ phải ở tương lai và kết thúc sau bắt đầu");
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
            WHERE r.employee_id=? AND r.status='DA_DUYET' AND d.approved_last_working_date<?
            """,LocalDate.class,employee,end.minusNanos(1).toLocalDate());
        if(!resigned.isEmpty()) throw PersonnelWorkflowSupport.conflict("Thời gian nghỉ vượt ngày làm cuối đã duyệt");
        var schedules=jdbc.queryForList("""
            SELECT schedule_id,work_date,planned_start,planned_end,planned_break_minutes FROM dbo.work_schedules WITH(UPDLOCK,HOLDLOCK)
            WHERE employee_id=? AND planned_start<? AND planned_end>? ORDER BY planned_start,schedule_id
            """,employee,end,start);
        if(schedules.isEmpty()) throw PersonnelWorkflowSupport.conflict("Chưa có lịch làm việc trong khoảng nghỉ");
        var charges=new ArrayList<Charge>();var totals=new HashMap<Long,BigDecimal>();LocalDateTime previousEnd=null;
        for(var row:schedules) {
            var a=((java.sql.Timestamp)row.get("planned_start")).toLocalDateTime();var b=((java.sql.Timestamp)row.get("planned_end")).toLocalDateTime();
            if(previousEnd!=null&&a.isBefore(previousEnd)) throw PersonnelWorkflowSupport.conflict("Lịch làm việc bị chồng thời gian, cần đối soát");previousEnd=b;
            var from=start.isAfter(a)?start:a;var to=end.isBefore(b)?end:b;
            int pause=((Number)row.get("planned_break_minutes")).intValue();
            if(pause>0&&(from.isAfter(a)||to.isBefore(b))) throw PersonnelWorkflowSupport.conflict("Nghỉ một phần ca có giờ nghỉ giữa ca chưa xác định vị trí; chọn trọn ca hoặc chia lịch chính xác");
            long millis=Duration.between(from,to).toMillis()-pause*60000L;
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
        for(var entry:totals.entrySet()) {
            BigDecimal granted=jdbc.queryForObject("SELECT entitled_hours FROM dbo.leave_entitlements WHERE entitlement_id=?",BigDecimal.class,entry.getKey());
            if(used(entry.getKey()).add(entry.getValue()).compareTo(granted)>0) throw PersonnelWorkflowSupport.conflict("Không đủ số giờ phép còn lại");
        }
        return charges;
    }
    public BigDecimal used(long entitlement) {
        return jdbc.queryForObject("""
            SELECT COALESCE(SUM(a.charged_hours),0) FROM dbo.g2_leave_allocations a JOIN dbo.employee_requests r ON r.request_id=a.request_id
            WHERE a.entitlement_id=? AND r.status='DA_DUYET'
            """,BigDecimal.class,entitlement);
    }
    public List<Map<String,Object>> balances(long employee,Principal p) {
        access.ownerOrManager(p,employee);
        return jdbc.queryForList("""
            SELECT e.entitlement_id AS entitlementId,e.leave_type_id AS leaveTypeId,
              CONVERT(varchar(10),e.period_start,23) AS periodStart,CONVERT(varchar(10),e.period_end,23) AS periodEnd,
              e.entitled_hours AS entitledHours,COALESCE(u.hours,0) AS usedHours,e.entitled_hours-COALESCE(u.hours,0) AS remainingHours
            FROM dbo.leave_entitlements e OUTER APPLY (
              SELECT SUM(a.charged_hours) AS hours FROM dbo.g2_leave_allocations a JOIN dbo.employee_requests r ON r.request_id=a.request_id
              WHERE a.entitlement_id=e.entitlement_id AND r.status='DA_DUYET') u
            WHERE e.employee_id=? ORDER BY e.period_start DESC,e.entitlement_id DESC
            """,employee);
    }
    private void checkResignation(long employee,LocalDate last) {
        long heads=jdbc.queryForObject("""
            SELECT COUNT_BIG(*) FROM dbo.employee_assignments a JOIN dbo.positions p ON p.position_id=a.position_id
            WHERE a.employee_id=? AND p.position_code='TRUONG_PHONG' AND (a.effective_to IS NULL OR a.effective_to>?)
            """,Long.class,employee,last.plusDays(1));
        if(heads>0) throw PersonnelWorkflowSupport.conflict("Phải kết thúc/thay trưởng phòng trước ngày nghỉ việc");
        long incompatible=jdbc.queryForObject("""
            SELECT (SELECT COUNT_BIG(*) FROM dbo.work_schedules WHERE employee_id=? AND planned_end>?)
              +(SELECT COUNT_BIG(*) FROM dbo.contracts WHERE employee_id=? AND status='DA_KY' AND (end_date IS NULL OR end_date>?))
              +(SELECT COUNT_BIG(*) FROM dbo.employee_assignments WHERE employee_id=? AND effective_from>=?)
              +(SELECT COUNT_BIG(*) FROM dbo.employee_requests r JOIN dbo.leave_request_details l ON l.request_id=r.request_id
                 WHERE r.employee_id=? AND r.status='DA_DUYET' AND l.leave_end>?)
            """,Long.class,employee,last.plusDays(1).atStartOfDay(),employee,last,employee,last.plusDays(1),employee,last.plusDays(1).atStartOfDay());
        if(incompatible>0) throw PersonnelWorkflowSupport.conflict("Còn lịch, hợp đồng, phân công tương lai hoặc phép đã duyệt vượt ngày nghỉ; phối hợp xử lý trước");
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
        jdbc.update("UPDATE dbo.accounts SET is_active=0,updated_at=?,updated_by=? WHERE employee_id=?",support.utc(),actor,employee);
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
