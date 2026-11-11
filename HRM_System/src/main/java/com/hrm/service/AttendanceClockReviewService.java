package com.hrm.service;
import com.hrm.security.AuthenticatedAccount;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
@Service
public class AttendanceClockReviewService {
 @org.springframework.beans.factory.annotation.Autowired(required=false) private AttendanceNotifications notifications;
 private final JdbcTemplate db;private final AttendancePortalService portal;
 public AttendanceClockReviewService(JdbcTemplate db,AttendancePortalService portal){this.db=db;this.portal=portal;}
 @Transactional("scheduleTransactionManager")
 public Object review(AuthenticatedAccount actor,long attendanceId,boolean checkIn,String expectedVersion,String reason){
  if(reason==null||reason.isBlank()||reason.length()>1000)throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Nhập lý do đối soát, tối đa 1000 ký tự.");
  long employee=db.queryForObject("SELECT employee_id FROM dbo.attendance_records WHERE attendance_id=? AND invalidated_at IS NULL",Long.class,attendanceId);
  db.queryForObject("SELECT employee_id FROM dbo.employees WITH(UPDLOCK,HOLDLOCK) WHERE employee_id=?",Long.class,employee);
  long schedule=db.queryForObject("SELECT schedule_id FROM dbo.attendance_records WITH(UPDLOCK,HOLDLOCK) WHERE attendance_id=? AND invalidated_at IS NULL",Long.class,attendanceId);
  var row=portal.detail(actor,false,schedule).row();
  if(!(checkIn?row.canReviewCheckIn():row.canReviewCheckOut()))throw new ResponseStatusException(HttpStatus.CONFLICT,"Không có quyền, giờ chưa được ghi nhận, đã đối soát hoặc kỳ đã khóa.");
  if(expectedVersion==null||!expectedVersion.equals(row.version()))throw new ResponseStatusException(HttpStatus.CONFLICT,"Công đã thay đổi; tải lại trước khi đối soát.");
  String prefix=checkIn?"check_in":"check_out",actual=checkIn?"actual_start":"actual_end";
  String before=snapshot(attendanceId);
  // SQL Server sessions may have NOCOUNT enabled; JDBC update counts then do not
  // identify matched records. OUTPUT INTO works with triggers and preserves version checks.
  Integer count=db.queryForObject("DECLARE @changed TABLE(id bigint); UPDATE dbo.attendance_records SET "+prefix+"_reviewed_by=?,"+prefix+"_reviewed_at=SYSUTCDATETIME(),"+prefix+"_reviewed_value="+actual+" OUTPUT INSERTED.attendance_id INTO @changed WHERE attendance_id=? AND row_version=? AND invalidated_at IS NULL; SELECT COUNT(*) FROM @changed",Integer.class,actor.accountId(),attendanceId,Base64.getDecoder().decode(expectedVersion));
  if(count==null||count!=1)throw new ResponseStatusException(HttpStatus.CONFLICT,"Công đã thay đổi đồng thời.");
  db.update("INSERT dbo.audit_logs(actor_account_id,action,target_table,target_record_key,reason,before_data_json,after_data_json) VALUES(?,?,'attendance_records',?,?,?,?)",actor.accountId(),checkIn?"ATTENDANCE_REVIEW_CHECK_IN":"ATTENDANCE_REVIEW_CHECK_OUT",Long.toString(attendanceId),reason.trim(),before,snapshot(attendanceId));
  if(notifications!=null)notifications.publishRecord(attendanceId,checkIn?"ATTENDANCE_REVIEW_CHECK_IN":"ATTENDANCE_REVIEW_CHECK_OUT",actor.accountId());
  return portal.detail(actor,false,schedule);
 }
 private String snapshot(long id){return db.queryForObject("SELECT (SELECT * FROM dbo.attendance_records WHERE attendance_id=? FOR JSON PATH,WITHOUT_ARRAY_WRAPPER)",String.class,id);}
}
