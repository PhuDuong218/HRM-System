package com.hrm.service;

import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

/** Called inside the business transaction. Never catch and suppress persistence failures. */
@Service
public class NotificationPublisher {
 private final JdbcTemplate db;private final boolean enabled;
 public NotificationPublisher(JdbcTemplate db,@Value("${hrm.notifications.enabled:false}") boolean enabled){this.db=db;this.enabled=enabled;}
 public boolean enabled(){return enabled;}
 public void enqueue(String key,Long actor,String type,String title,String message,String referenceType,long referenceId,Collection<Long> recipients){
  if(!enabled)return;
  if(!org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive())throw new org.springframework.transaction.IllegalTransactionStateException("Outbox requires a business transaction");
  var ids=new TreeSet<Long>();for(Long id:recipients)if(id!=null&&id>0)ids.add(id);if(actor!=null)ids.remove(actor);if(ids.isEmpty())return;
  if(key==null||key.isBlank()||key.length()>200||referenceId<=0)throw new IllegalArgumentException("Invalid notification event");
  // Range lock + UNIQUE key handles retries without aborting an outer transaction on duplicate inserts.
  var existing=db.queryForList("SELECT event_id FROM dbo.NotificationOutbox WITH(UPDLOCK,HOLDLOCK) WHERE idempotency_key=?",String.class,key);
  if(!existing.isEmpty())return;
  String event=UUID.randomUUID().toString();
  db.update("INSERT dbo.NotificationOutbox(event_id,idempotency_key,actor_id,event_type,title,message,reference_type,reference_id) VALUES(?,?,?,?,?,?,?,?)",event,key,actor,type,title,message,referenceType,referenceId);
  for(long id:ids)db.update("INSERT dbo.NotificationOutboxRecipients(event_id,recipient_id) VALUES(?,?)",event,id);
 }
}
