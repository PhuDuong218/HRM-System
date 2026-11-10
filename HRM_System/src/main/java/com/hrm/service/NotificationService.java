package com.hrm.service;

import java.security.Principal;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

@Service
public class NotificationService {
 @org.springframework.beans.factory.annotation.Autowired private AttendancePortalService attendance;
 private final JdbcTemplate db;private final PersonnelAccessService access;private final NotificationPublisher publisher;private final EmployeeRequestService requests;
 public NotificationService(JdbcTemplate db,PersonnelAccessService access,NotificationPublisher publisher,EmployeeRequestService requests){this.db=db;this.access=access;this.publisher=publisher;this.requests=requests;}
 private long owner(Principal p){if(!publisher.enabled())throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,"Thông báo chưa được kích hoạt");return access.actor(p).accountId();}
 public record Inbox(List<Map<String,Object>> items,long totalElements,int page,int totalPages,long unreadCount,long snapshotId){}
 @Transactional(readOnly=true)
 public Inbox list(Boolean read,int page,int size,Long snapshot,Principal p){
  long owner=owner(p);if(page<0||size<1||size>50||snapshot!=null&&snapshot<0)throw PersonnelWorkflowSupport.bad("Phân trang không hợp lệ");
  long limit=snapshot==null?db.queryForObject("SELECT COALESCE(MAX(notification_id),0) FROM dbo.Notifications WHERE recipient_id=?",Long.class,owner):snapshot;
  String filter=read==null?"":" AND is_read=?";List<Object> args=new ArrayList<>(List.of(owner,limit));if(read!=null)args.add(read);
  long count=db.queryForObject("SELECT COUNT_BIG(*) FROM dbo.Notifications WHERE recipient_id=? AND notification_id<=?"+filter,Long.class,args.toArray());
  args.add((long)page*size);args.add(size);
  var rows=db.queryForList("SELECT notification_id AS id,event_type AS eventType,title,message,reference_type AS referenceType,reference_id AS referenceId,is_read AS isRead,CONVERT(varchar(23),created_at,126)+'Z' AS createdAt,CASE WHEN read_at IS NOT NULL THEN CONVERT(varchar(23),read_at,126)+'Z' END AS readAt FROM dbo.Notifications WHERE recipient_id=? AND notification_id<=?"+filter+" ORDER BY notification_id DESC OFFSET ? ROWS FETCH NEXT ? ROWS ONLY",args.toArray());
  return new Inbox(rows,count,page,(int)((count+size-1)/size),count(owner),limit);
 }
 private long count(long owner){return db.queryForObject("SELECT COUNT_BIG(*) FROM dbo.Notifications WHERE recipient_id=? AND is_read=0",Long.class,owner);}
 @Transactional(readOnly=true)
 public Map<String,Long> unread(Principal p){return Map.of("unreadCount",count(owner(p)));}
 @Transactional
 public void read(long id,Principal p){long owner=owner(p);
  var exists=db.queryForList("SELECT notification_id FROM dbo.Notifications WHERE notification_id=? AND recipient_id=?",Long.class,id,owner);
  if(exists.isEmpty())throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Không tìm thấy thông báo");
  db.update("UPDATE dbo.Notifications SET is_read=1,read_at=SYSUTCDATETIME() WHERE notification_id=? AND recipient_id=? AND is_read=0",id,owner);
 }
 @Transactional
 public void readAll(long throughId,Principal p){if(throughId<0)throw PersonnelWorkflowSupport.bad("Mốc thông báo không hợp lệ");long owner=owner(p);db.update("UPDATE dbo.Notifications SET is_read=1,read_at=SYSUTCDATETIME() WHERE recipient_id=? AND notification_id<=? AND is_read=0",owner,throughId);}
 public Map<String,Object> target(long id,Principal p){long owner=owner(p);
  var rows=db.queryForList("SELECT reference_type,reference_id FROM dbo.Notifications WHERE notification_id=? AND recipient_id=?",id,owner);
  if(rows.isEmpty())throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Không tìm thấy thông báo");
  var row=rows.get(0);
  if("attendance_portal_requests".equals(row.get("reference_type"))){
   long reference=((Number)row.get("reference_id")).longValue();var actor=access.actor(p);
   var identity=new com.hrm.security.AuthenticatedAccount(actor.accountId(),actor.employeeId(),p.getName(),actor.role());
   var senders=db.queryForList("SELECT submitted_by FROM dbo.attendance_portal_requests WHERE request_id=?",Long.class,reference);
   if(senders.isEmpty())throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Yêu cầu không còn tồn tại");
   var request=attendance.request(identity,senders.get(0)==actor.accountId(),reference).request();
   var detail=new LinkedHashMap<String,Object>();detail.put("requestCode",request.requestCode());detail.put("requestType","ATTENDANCE_REQUEST");detail.put("status",request.status());detail.put("reason",request.reason());detail.put("submittedAt",request.submittedAt()+"Z");detail.put("proposedStart",request.proposedStart());detail.put("proposedEnd",request.proposedEnd());detail.put("decisionReason",request.decisionReason());detail.put("receiver",request.receiver());detail.put("receivedAt",request.receivedAt()==null?null:request.receivedAt()+"Z");detail.put("processedAt",request.processedAt()==null?null:request.processedAt()+"Z");
   return Map.of("referenceType","attendance_portal_requests","referenceId",reference,"detail",detail);
  }
  if(!"employee_requests".equals(row.get("reference_type")))throw new ResponseStatusException(HttpStatus.CONFLICT,"Chưa hỗ trợ mở loại thông báo này");
  long reference=((Number)row.get("reference_id")).longValue();var detail=requests.detail(reference,p); // Re-evaluate current RBAC/scope; old notifications never grant access.
  return Map.of("referenceType","employee_requests","referenceId",reference,"detail",detail);
 }
}
