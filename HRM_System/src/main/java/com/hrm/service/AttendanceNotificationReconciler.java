package com.hrm.service;
import org.springframework.stereotype.Component;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
@Component
public class AttendanceNotificationReconciler {
 private final JdbcTemplate db;private final NotificationPublisher publisher;private final AttendanceNotifications notifications;
 public AttendanceNotificationReconciler(JdbcTemplate db,NotificationPublisher publisher,AttendanceNotifications notifications){this.db=db;this.publisher=publisher;this.notifications=notifications;}
 @Scheduled(fixedDelay=30000,initialDelay=7000)
 public void run(){if(!publisher.enabled())return;
  for(long id:db.queryForList("SELECT TOP(100) r.request_id FROM dbo.attendance_portal_requests r WHERE r.status='CHO_XU_LY' AND NOT EXISTS(SELECT 1 FROM dbo.NotificationOutbox o WHERE o.idempotency_key=CONCAT('attendance_portal_requests:',r.request_id,':SUBMITTED')) ORDER BY r.request_id",Long.class))notifications.reconcile(id);
 }
}
