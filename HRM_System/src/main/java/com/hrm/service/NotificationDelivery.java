package com.hrm.service;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

/** One event is delivered atomically, allowing multiple server instances to compete safely. */
@Service
public class NotificationDelivery {
 private final JdbcTemplate db;
 public NotificationDelivery(JdbcTemplate db){this.db=db;}
 @Transactional(propagation=Propagation.REQUIRES_NEW)
 public void deliver(String id){
  var rows=db.queryForList("SELECT event_id FROM dbo.NotificationOutbox WITH(UPDLOCK,READPAST,READCOMMITTEDLOCK) WHERE event_id=? AND status IN ('PENDING','RETRY') AND available_at<=SYSUTCDATETIME()",id);
  if(rows.isEmpty())return;
  db.update("""
   INSERT dbo.Notifications(event_id,recipient_id,event_type,title,message,reference_type,reference_id)
   SELECT o.event_id,r.recipient_id,o.event_type,o.title,o.message,o.reference_type,o.reference_id
   FROM dbo.NotificationOutbox o JOIN dbo.NotificationOutboxRecipients r ON r.event_id=o.event_id
   WHERE o.event_id=? AND NOT EXISTS(SELECT 1 FROM dbo.Notifications n WITH(UPDLOCK,HOLDLOCK) WHERE n.event_id=o.event_id AND n.recipient_id=r.recipient_id)
   """,id);
  db.update("UPDATE dbo.NotificationOutbox SET status='PROCESSED',processed_at=SYSUTCDATETIME(),attempt_count=attempt_count+1,last_error=NULL WHERE event_id=?",id);
 }
 @Transactional(propagation=Propagation.REQUIRES_NEW)
 public void failed(String id){
  db.update("""
   UPDATE dbo.NotificationOutbox SET attempt_count=attempt_count+1,
   status=CASE WHEN attempt_count+1>=8 THEN 'DEAD' ELSE 'RETRY' END,
   available_at=DATEADD(second,CASE WHEN attempt_count>=6 THEN 900 ELSE CAST(POWER(2,attempt_count+1)*5 AS int) END,SYSUTCDATETIME()),
   last_error=N'Delivery failed. Inspect server logs using event_id.'
   WHERE event_id=? AND status IN ('PENDING','RETRY') AND available_at<=SYSUTCDATETIME()
   """,id);
 }
}
