package com.hrm.service;
import java.time.LocalDate;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import static org.mockito.Mockito.*;
class AttendanceNotificationRoutingTest {
 @Test void onlyIndependentManagersInScopeReceiveSubmittedRequest(){
  var db=mock(JdbcTemplate.class);var publisher=mock(NotificationPublisher.class);var permissions=mock(AttendancePermissionService.class);when(publisher.enabled()).thenReturn(true);
  LocalDate date=LocalDate.of(2026,11,9);when(db.queryForMap(anyString(),eq(7L))).thenReturn(Map.of("employee_id",9L,"submitted_by",42L,"work_date",java.sql.Date.valueOf(date)));
  when(db.queryForList(anyString(),eq(Long.class))).thenReturn(List.of(11L,12L,42L));
  doThrow(new ResponseStatusException(HttpStatus.FORBIDDEN)).when(permissions).requireAttendanceAccess(12,9,date,AttendancePermissionService.MANAGE);
  new AttendanceNotifications(db,publisher,permissions).publish(7,"SUBMITTED",42L);
  verify(publisher).enqueue(eq("attendance_portal_requests:7:SUBMITTED"),eq(42L),eq("ATTENDANCE_SUBMITTED"),anyString(),anyString(),eq("attendance_portal_requests"),eq(7L),eq(List.of(11L)));
  verify(permissions,never()).requireAttendanceAccess(eq(42L),anyLong(),any(),anyString());
 }
 @Test void processingResultIsSentOnlyToOriginalSender(){
  var db=mock(JdbcTemplate.class);var publisher=mock(NotificationPublisher.class);var permissions=mock(AttendancePermissionService.class);when(publisher.enabled()).thenReturn(true);
  when(db.queryForMap(anyString(),eq(7L))).thenReturn(Map.of("employee_id",9L,"submitted_by",42L,"work_date",java.sql.Date.valueOf("2026-11-09")));
  new AttendanceNotifications(db,publisher,permissions).publish(7,"APPROVED",11L);
  verify(publisher).enqueue(anyString(),eq(11L),eq("ATTENDANCE_APPROVED"),anyString(),anyString(),eq("attendance_portal_requests"),eq(7L),eq(List.of(42L)));verifyNoInteractions(permissions);
 }

 @Test void clockReviewIsDeliveredToEmployeesAccountsWithVersionedKey(){
  var db=mock(JdbcTemplate.class);var publisher=mock(NotificationPublisher.class);var permissions=mock(AttendancePermissionService.class);when(publisher.enabled()).thenReturn(true);
  when(db.queryForMap(anyString(),eq(15L))).thenReturn(Map.of("employee_id",9L,"work_date",java.sql.Date.valueOf("2026-11-09"),"version","0x0000000000000012"));
  when(db.queryForList(anyString(),eq(Long.class),eq(9L))).thenReturn(List.of(42L));
  var notifications=new AttendanceNotifications(db,publisher,permissions);
  for(String action:List.of("ATTENDANCE_REVIEW_CHECK_IN","ATTENDANCE_REVIEW_CHECK_OUT","ATTENDANCE_CREATE","ATTENDANCE_CORRECT","ATTENDANCE_CONFIRM","ATTENDANCE_UNCONFIRM")){
   notifications.publishRecord(15,action,11L);
   verify(publisher).enqueue(eq("attendance_records:15:"+action+":0x0000000000000012"),eq(11L),eq(action),anyString(),anyString(),eq("attendance_records"),eq(15L),eq(List.of(42L)));
  }
  verifyNoInteractions(permissions);
 }
 @Test void disabledRecordNotificationsDoNotTouchDatabase(){
  var db=mock(JdbcTemplate.class);var publisher=mock(NotificationPublisher.class);
  new AttendanceNotifications(db,publisher,mock(AttendancePermissionService.class)).publishRecord(15,"ATTENDANCE_REVIEW_CHECK_IN",11L);
  verifyNoInteractions(db);
 }
}
