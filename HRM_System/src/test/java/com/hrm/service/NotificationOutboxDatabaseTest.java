package com.hrm.service;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import static org.junit.jupiter.api.Assertions.*;

/** Opt-in test: uses new notification tables only; every event is rolled back. */
@SpringBootTest(properties={"hrm.notifications.enabled=true","hrm.notifications.initial-delay-ms=3600000"})
@EnabledIfSystemProperty(named="notifications.db.verify",matches="true")
class NotificationOutboxDatabaseTest {
 @Autowired NotificationPublisher publisher;@Autowired JdbcTemplate db;@Autowired PlatformTransactionManager manager;
 @Test void mandatoryTransactionAndBusinessFailureLeaveNoEvent(){
  assertEquals("hrm_center_full",db.queryForObject("SELECT DB_NAME()",String.class));
  long recipient=db.queryForObject("SELECT TOP(1) account_id FROM dbo.accounts WHERE is_active=1 ORDER BY account_id",Long.class);
  String key="rollback-test:"+UUID.randomUUID();
  assertThrows(org.springframework.transaction.IllegalTransactionStateException.class,()->publisher.enqueue(key,null,"TEST","Title","Message","employee_requests",1,List.of(recipient)));
  assertThrows(IllegalStateException.class,()->new TransactionTemplate(manager).execute(s->{publisher.enqueue(key,null,"TEST","Title","Message","employee_requests",1,List.of(recipient));throw new IllegalStateException("Business failure");}));
  assertEquals(0,db.queryForObject("SELECT COUNT(*) FROM dbo.NotificationOutbox WHERE idempotency_key=?",Integer.class,key));
 }
 @Test void retryingPublisherWithinOneBusinessTransactionIsIdempotent(){
  long recipient=db.queryForObject("SELECT TOP(1) account_id FROM dbo.accounts WHERE is_active=1 ORDER BY account_id",Long.class);String key="dedupe-test:"+UUID.randomUUID();
  new TransactionTemplate(manager).execute(s->{publisher.enqueue(key,null,"TEST","Title","Message","employee_requests",1,List.of(recipient,recipient));publisher.enqueue(key,null,"TEST","Title","Message","employee_requests",1,List.of(recipient));assertEquals(1,db.queryForObject("SELECT COUNT(*) FROM dbo.NotificationOutbox WHERE idempotency_key=?",Integer.class,key));assertEquals(1,db.queryForObject("SELECT COUNT(*) FROM dbo.NotificationOutboxRecipients r JOIN dbo.NotificationOutbox o ON o.event_id=r.event_id WHERE o.idempotency_key=?",Integer.class,key));s.setRollbackOnly();return null;});
  assertEquals(0,db.queryForObject("SELECT COUNT(*) FROM dbo.NotificationOutbox WHERE idempotency_key=?",Integer.class,key));
 }
 @Test void workerSqlDeliversOnceAndAtomicallyCompletesOutbox(){
  long recipient=db.queryForObject("SELECT TOP(1) account_id FROM dbo.accounts WHERE is_active=1 ORDER BY account_id",Long.class);String key="delivery-test:"+UUID.randomUUID();
  new TransactionTemplate(manager).execute(s->{
   publisher.enqueue(key,null,"TEST","Title","Message","employee_requests",1,List.of(recipient));
   String event=db.queryForObject("SELECT CONVERT(varchar(36),event_id) FROM dbo.NotificationOutbox WHERE idempotency_key=?",String.class,key);
   // Exercise SQL in the test transaction so no inbox test data is committed.
   var worker=new NotificationDelivery(db);worker.deliver(event);worker.deliver(event);
   assertEquals(1,db.queryForObject("SELECT COUNT(*) FROM dbo.Notifications WHERE event_id=?",Integer.class,event));
   assertEquals("PROCESSED",db.queryForObject("SELECT status FROM dbo.NotificationOutbox WHERE event_id=?",String.class,event));
   s.setRollbackOnly();return null;
  });
 }
}
