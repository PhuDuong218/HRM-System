package com.hrm.service;
import java.time.LocalDate;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AttendanceNotifications {
 private final JdbcTemplate db;private final NotificationPublisher publisher;private final AttendancePermissionService permissions;
 public AttendanceNotifications(JdbcTemplate db,NotificationPublisher publisher,AttendancePermissionService permissions){this.db=db;this.publisher=publisher;this.permissions=permissions;}
 @Transactional(value="scheduleTransactionManager",propagation=Propagation.MANDATORY)
 public void publish(long id,String action,Long actor){
  if(!publisher.enabled())return;
  var row=db.queryForMap("SELECT r.submitted_by,s.employee_id,s.work_date FROM dbo.attendance_portal_requests r JOIN dbo.work_schedules s ON s.schedule_id=r.schedule_id WHERE r.request_id=?",id);
  long employee=((Number)row.get("employee_id")).longValue(),sender=((Number)row.get("submitted_by")).longValue();
  LocalDate date=((java.sql.Date)row.get("work_date")).toLocalDate();
  List<Long> recipients=new ArrayList<>();
  if("SUBMITTED".equals(action)){
   for(long candidate:db.queryForList("SELECT ac.account_id FROM dbo.accounts ac JOIN dbo.roles role ON role.role_id=ac.role_id JOIN dbo.employees e ON e.employee_id=ac.employee_id WHERE ac.is_active=1 AND e.employment_status='DANG_LAM' AND role.role_code IN ('ADMIN','HR')",Long.class)){
    if(candidate==sender)continue;
    try{permissions.requireAttendanceAccess(candidate,employee,date,AttendancePermissionService.MANAGE);permissions.requireAttendanceAccess(candidate,employee,date,AttendancePermissionService.CONFIRM);recipients.add(candidate);}
    catch(ResponseStatusException e){if(e.getStatusCode().value()!=403)throw e;}
   }
  }else recipients.add(sender);
  String message=switch(action){case "SUBMITTED"->"Có yêu cầu bổ sung / điều chỉnh công đang chờ xử lý.";case "RECEIVED"->"Yêu cầu công của bạn đã được tiếp nhận.";case "APPROVED"->"Yêu cầu công của bạn đã được chấp thuận.";case "REJECTED"->"Yêu cầu công của bạn đã bị từ chối. Mở chi tiết để xem lý do.";default->throw new IllegalArgumentException("Invalid attendance event");};
  publisher.enqueue("attendance_portal_requests:"+id+":"+action,actor,"ATTENDANCE_"+action,"Cập nhật yêu cầu chấm công",message,"attendance_portal_requests",id,recipients);
 }
 @Transactional("scheduleTransactionManager")
 public void reconcile(long id){
  if(!publisher.enabled())return;
  var rows=db.queryForList("SELECT submitted_by FROM dbo.attendance_portal_requests WITH(UPDLOCK,HOLDLOCK) WHERE request_id=? AND status='CHO_XU_LY'",Long.class,id);
  if(!rows.isEmpty())publish(id,"SUBMITTED",rows.get(0));
 }
}
