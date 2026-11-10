package com.hrm.service;
import org.springframework.stereotype.Component;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.jdbc.core.JdbcTemplate;
import org.slf4j.Logger;import org.slf4j.LoggerFactory;
@Component
public class NotificationDispatcher {
 private static final Logger log=LoggerFactory.getLogger(NotificationDispatcher.class);
 private final JdbcTemplate db;private final NotificationPublisher publisher;private final NotificationDelivery delivery;
 public NotificationDispatcher(JdbcTemplate db,NotificationPublisher publisher,NotificationDelivery delivery){this.db=db;this.publisher=publisher;this.delivery=delivery;}
 @Scheduled(fixedDelayString="${hrm.notifications.dispatch-ms:1000}",initialDelayString="${hrm.notifications.initial-delay-ms:5000}")
 public void run(){if(!publisher.enabled())return;
  for(String id:db.queryForList("SELECT TOP(25) CONVERT(varchar(36),event_id) FROM dbo.NotificationOutbox WHERE status IN ('PENDING','RETRY') AND available_at<=SYSUTCDATETIME() ORDER BY created_at,event_id",String.class)){
   try{delivery.deliver(id);}catch(RuntimeException e){log.warn("Notification delivery failed for event {} ({})",id,e.getClass().getSimpleName());delivery.failed(id);}
  }
 }
}
