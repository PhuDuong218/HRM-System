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
    try{permissions.requireAttendanceAccess(candidate,employee,date,AttendancePermissionService.MANAGE);recipients.add(candidate);}
    catch(ResponseStatusException e){if(e.getStatusCode().value()!=403)throw e;}
   }
   recipients.addAll(db.queryForList("SELECT DISTINCT ac.account_id FROM dbo.employee_assignments member JOIN dbo.employee_assignments head ON head.department_id=member.department_id JOIN dbo.positions pos ON pos.position_id=head.position_id AND pos.position_code='TRUONG_PHONG' JOIN dbo.accounts ac ON ac.employee_id=head.employee_id AND ac.is_active=1 JOIN dbo.employees leader ON leader.employee_id=head.employee_id AND leader.employment_status='DANG_LAM' WHERE member.employee_id=? AND member.effective_from<=? AND (member.effective_to IS NULL OR member.effective_to>?) AND head.effective_from<=CONVERT(date,SYSDATETIMEOFFSET() AT TIME ZONE 'SE Asia Standard Time') AND (head.effective_to IS NULL OR head.effective_to>CONVERT(date,SYSDATETIMEOFFSET() AT TIME ZONE 'SE Asia Standard Time')) AND head.employee_id<>?",Long.class,employee,date,date,employee));
  }else recipients.add(sender);
  String message=switch(action){case "SUBMITTED"->"Có yêu cầu bổ sung / điều chỉnh công đang chờ xử lý.";case "RECEIVED"->"Yêu cầu công của bạn đã được tiếp nhận.";case "APPROVED"->"Yêu cầu công của bạn đã được chấp thuận.";case "REJECTED"->"Yêu cầu công của bạn đã bị từ chối. Mở chi tiết để xem lý do.";default->throw new IllegalArgumentException("Invalid attendance event");};
  publisher.enqueue("attendance_portal_requests:"+id+":"+action,actor,"ATTENDANCE_"+action,"Cập nhật yêu cầu chấm công",message,"attendance_portal_requests",id,recipients);
 }
 @Transactional(value="scheduleTransactionManager",propagation=Propagation.MANDATORY)
 public void publishRecord(long id,String action,Long actor){
  if(!publisher.enabled())return;
  String message=switch(action){
   case "ATTENDANCE_REVIEW_CHECK_IN"->"Giờ check-in của bạn đã được đối soát.";
   case "ATTENDANCE_REVIEW_CHECK_OUT"->"Giờ check-out của bạn đã được đối soát.";
   case "ATTENDANCE_CREATE"->"Công của bạn đã được bổ sung.";
   case "ATTENDANCE_CORRECT"->"Công của bạn đã được điều chỉnh.";
   case "ATTENDANCE_CONFIRM"->"Công của bạn đã được xác nhận.";
   case "ATTENDANCE_UNCONFIRM"->"Công của bạn đã được hủy xác nhận.";
   default->null;
  };
  if(message==null)return;
  var row=db.queryForMap("SELECT employee_id,work_date,CONVERT(varchar(18),row_version,1) version FROM dbo.attendance_records WHERE attendance_id=? AND invalidated_at IS NULL",id);
  var recipients=db.queryForList("SELECT account_id FROM dbo.accounts WHERE employee_id=? AND is_active=1",Long.class,row.get("employee_id"));
  publisher.enqueue("attendance_records:"+id+":"+action+":"+row.get("version"),actor,action,"Cập nhật chấm công",message+" Ngày ca: "+row.get("work_date")+".","attendance_records",id,recipients);
 }
 @Transactional("scheduleTransactionManager")
 public void reconcile(long id){
  if(!publisher.enabled())return;
  var rows=db.queryForList("SELECT submitted_by FROM dbo.attendance_portal_requests WITH(UPDLOCK,HOLDLOCK) WHERE request_id=? AND status='CHO_XU_LY'",Long.class,id);
  if(!rows.isEmpty())publish(id,"SUBMITTED",rows.get(0));
 }
}
