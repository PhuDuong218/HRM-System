package com.hrm.service;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

@Service
public class WorkflowNotifications {
 private final JdbcTemplate db;private final NotificationPublisher publisher;
 public WorkflowNotifications(JdbcTemplate db,NotificationPublisher publisher){this.db=db;this.publisher=publisher;}
 @Transactional(propagation=Propagation.MANDATORY)
 public void request(long id,String action,long actor){
  if(!publisher.enabled())return;
  var row=db.queryForMap("SELECT employee_id,request_type FROM dbo.employee_requests WHERE request_id=?",id);
  long employee=((Number)row.get("employee_id")).longValue();
  String type="NGHI_VIEC".equals(row.get("request_type"))?"nghỉ việc":"nghỉ phép";
  String key="employee_requests:"+id+":"+action;
  if("SUBMITTED".equals(action)||"HEAD_APPROVED".equals(action)){
   List<Long> next=List.of();
   if("SUBMITTED".equals(action))next=db.queryForList("""
    SELECT DISTINCT a.account_id FROM dbo.personnel_request_approval_stages s
    JOIN dbo.employee_assignments h ON h.department_id=s.department_id
    JOIN dbo.positions p ON p.position_id=h.position_id AND p.position_code='TRUONG_PHONG'
    JOIN dbo.accounts a ON a.employee_id=h.employee_id AND a.is_active=1
    JOIN dbo.employees e ON e.employee_id=a.employee_id AND e.employment_status='DANG_LAM'
    WHERE s.request_id=? AND s.stage_status='PENDING' AND a.employee_id<>?
    AND h.effective_from<=CONVERT(date,SYSDATETIMEOFFSET() AT TIME ZONE 'SE Asia Standard Time')
    AND (h.effective_to IS NULL OR h.effective_to>CONVERT(date,SYSDATETIMEOFFSET() AT TIME ZONE 'SE Asia Standard Time'))
    """,Long.class,id,employee);
   // Never bypass an existing pending head stage merely because its account is inactive.
   Integer pending=db.queryForObject("SELECT COUNT(*) FROM dbo.personnel_request_approval_stages WHERE request_id=? AND stage_status='PENDING'",Integer.class,id);
   if(next.isEmpty()&&pending==0)next=approvers(employee);
   publisher.enqueue(key+":review",actor,"REQUEST_REVIEW_REQUIRED","Có đơn "+type+" cần xử lý","Một đơn "+type+" đang chờ bạn xem xét.","employee_requests",id,next);
  }
  if(!"SUBMITTED".equals(action)){
   String message=switch(action){case "HEAD_APPROVED"->"Đơn của bạn đã được duyệt cấp phòng, đang chờ duyệt cuối.";case "APPROVED"->"Đơn của bạn đã được phê duyệt cuối.";case "REJECTED","HEAD_REJECTED"->"Đơn của bạn đã bị từ chối. Mở chi tiết để xem lý do.";case "WITHDRAWN"->"Đơn đã được rút hoặc hủy.";default->throw new IllegalArgumentException("Unsupported notification action");};
   var owners=db.queryForList("SELECT account_id FROM dbo.accounts WHERE employee_id=? AND is_active=1",Long.class,employee);
   publisher.enqueue(key+":owner",actor,"REQUEST_"+action,"Cập nhật đơn "+type,message,"employee_requests",id,owners);
  }
 }
 private List<Long> approvers(long employee){return db.queryForList("""
  SELECT DISTINCT ac.account_id FROM dbo.accounts ac JOIN dbo.roles r ON r.role_id=ac.role_id
  JOIN dbo.employees manager ON manager.employee_id=ac.employee_id AND manager.employment_status='DANG_LAM'
  WHERE ac.is_active=1 AND ac.employee_id<>? AND r.role_code IN ('ADMIN','HR')
   AND EXISTS(SELECT 1 FROM dbo.role_permissions rp JOIN dbo.permissions p ON p.permission_id=rp.permission_id WHERE rp.role_id=r.role_id AND p.permission_code='REQUEST_APPROVE')
   AND (r.role_code='ADMIN' OR EXISTS(
    SELECT 1 FROM dbo.hr_department_scopes hs JOIN dbo.employee_assignments ea ON ea.department_id=hs.department_id
    WHERE hs.hr_account_id=ac.account_id AND hs.revoked_at IS NULL AND hs.effective_from<=SYSUTCDATETIME() AND (hs.effective_to IS NULL OR hs.effective_to>SYSUTCDATETIME())
    AND ea.employee_id=? AND ea.effective_from<=CONVERT(date,SYSDATETIMEOFFSET() AT TIME ZONE 'SE Asia Standard Time')
    AND (ea.effective_to IS NULL OR ea.effective_to>CONVERT(date,SYSDATETIMEOFFSET() AT TIME ZONE 'SE Asia Standard Time'))))
  """,Long.class,employee,employee);}
}
