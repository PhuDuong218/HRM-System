package com.hrm.service;
import java.time.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.web.server.ResponseStatusException;
import static org.junit.jupiter.api.Assertions.*;
import com.hrm.dto.AttendanceDTOs.*;
@EnabledIfSystemProperty(named="hrm.attendance.sqlserver-tests",matches="true")
class AttendanceClockReviewIntegrationTest {
 static AttendancePortalIntegrationTest t;static AttendanceClockReviewService review;
 @BeforeAll static void setup()throws Exception{AttendancePortalIntegrationTest.setup();t=new AttendancePortalIntegrationTest();review=AttendancePortalIntegrationTest.proxy(new AttendanceClockReviewService(t.db,t.service));}
 void clear(long id){t.db.update("UPDATE dbo.attendance_records SET check_in_reviewed_at=NULL,check_out_reviewed_at=NULL,check_in_reviewed_value=NULL,check_out_reviewed_value=NULL WHERE attendance_id=?",id);}
 @Test void checkInAndCheckOutAreReviewedAndLockedSeparately(){t.rollback(()->{
  long sid=t.fixture(t.employee,1);LocalDate day=LocalDate.now(t.clock).minusDays(1);var a=t.seed(new AttendanceInput(t.employee.employeeId(),sid,day,day.atTime(0,5),null,0,"DI_LAM","fixture"));clear(a.attendanceId());
  var row=t.service.detail(t.admin,false,sid).row();assertTrue(row.canReviewCheckIn());assertFalse(row.canReviewCheckOut());
  assertThrows(ResponseStatusException.class,()->review.review(t.admin,a.attendanceId(),false,row.version(),"Chưa ra"));
  review.review(t.admin,a.attendanceId(),true,row.version(),"Đã kiểm tra giờ vào");
  var checked=t.service.detail(t.admin,false,sid).row();assertTrue(checked.checkInReviewed());assertFalse(checked.canReviewCheckIn());assertFalse(checked.canConfirm());assertFalse(checked.canReviewCheckOut());
  assertThrows(ResponseStatusException.class,()->review.review(t.admin,a.attendanceId(),true,checked.version(),"Lặp"));
  assertThrows(ResponseStatusException.class,()->t.attendance.correct(a.attendanceId(),new AttendanceCorrection(day.atTime(0,6),null,0,"DI_LAM","Sửa không có đơn"),t.admin.accountId()));
  t.db.update("UPDATE dbo.attendance_records SET actual_end=? WHERE attendance_id=?",day.atTime(0,35),a.attendanceId());
  var out=t.service.detail(t.admin,false,sid).row();assertTrue(out.checkInReviewed());assertTrue(out.canReviewCheckOut());
  assertThrows(ResponseStatusException.class,()->review.review(t.admin,a.attendanceId(),false,checked.version(),"Phiên bản cũ"));
  review.review(t.admin,a.attendanceId(),false,out.version(),"Đã kiểm tra giờ ra");
  assertTrue(t.service.detail(t.admin,false,sid).row().canConfirm());t.service.verify(t.admin,a.attendanceId(),true,null);
  assertEquals("DA_XAC_NHAN",t.service.detail(t.admin,false,sid).row().verificationStatus());
 });}
 @Test void correctionRequiresEmployeeRequestAndRefreshesBothReviews(){t.rollback(()->{
  long sid=t.fixture(t.employee,1);var a=t.manual(sid,t.employee,1);clear(a.attendanceId());
  assertThrows(ResponseStatusException.class,()->t.service.verify(t.admin,a.attendanceId(),true,null));
  assertThrows(ResponseStatusException.class,()->t.attendance.correct(a.attendanceId(),new AttendanceCorrection(a.actualStart().plusMinutes(1),a.actualEnd(),0,"DI_LAM","Không có đơn"),t.admin.accountId()));
  long request=t.service.submit(t.employee,new AttendancePortalService.Proposal("UNCONFIRMED_CORRECTION",a.attendanceId(),a.actualStart().plusMinutes(1),a.actualEnd(),0,"DI_LAM","Nhân viên đề nghị sửa"));
  t.service.receive(t.admin,request);t.service.decide(t.admin,request,new AttendancePortalService.Decision(true,null,"Đã đối chiếu"));
  var row=t.service.detail(t.employee,true,sid).row();assertEquals(a.actualStart().plusMinutes(1),row.actualStart());assertTrue(row.checkInReviewed());assertTrue(row.checkOutReviewed());assertEquals("DA_XAC_NHAN",row.verificationStatus());
 });}
 @Test void employeeCannotReviewAttendance(){t.rollback(()->{
  long sid=t.fixture(t.employee,1);var a=t.manual(sid,t.employee,1);clear(a.attendanceId());
  assertThrows(ResponseStatusException.class,()->review.review(t.employee,a.attendanceId(),true,t.service.detail(t.employee,true,sid).row().version(),"Tự đối soát"));
 });}

 @Test void missingCheckoutCanBeMarkedAbsentOnlyAfterDeadline(){t.rollback(()->{
  long sid=t.fixture(t.employee,8);var day=LocalDate.now(t.clock).minusDays(8);var a=t.seed(new AttendanceInput(t.employee.employeeId(),sid,day,day.atTime(0,5),null,0,"DI_LAM","fixture missing checkout"));
  assertTrue(t.service.detail(t.admin,false,sid).row().canMarkAbsent());
  var absent=t.service.markAbsent(t.admin,sid,"Hết hạn giải trình, chưa có yêu cầu bổ sung giờ ra");assertEquals("VANG",absent.attendanceResult());assertNull(absent.actualStart());assertNull(absent.actualEnd());
  assertNotNull(t.db.queryForObject("SELECT TOP(1) before_data_json FROM dbo.audit_logs WHERE target_table='attendance_records' AND target_record_key=? AND action='ATTENDANCE_CORRECT' ORDER BY audit_id DESC",String.class,Long.toString(a.attendanceId())));
 });}
 @Test void reviewAuditFailureRollsBackReviewFlags(){t.rollback(()->{
  long sid=t.fixture(t.employee,1);var a=t.manual(sid,t.employee,1);clear(a.attendanceId());
  var broken=org.mockito.Mockito.spy(t.db);
  org.mockito.Mockito.doThrow(new org.springframework.dao.DataIntegrityViolationException("audit failure")).when(broken).update(org.mockito.ArgumentMatchers.startsWith("INSERT dbo.audit_logs("),org.mockito.ArgumentMatchers.any(Object[].class));
  var failing=AttendancePortalIntegrationTest.proxy(new AttendanceClockReviewService(broken,t.service));
  var nested=new org.springframework.transaction.support.TransactionTemplate(t.manager);nested.setPropagationBehavior(org.springframework.transaction.TransactionDefinition.PROPAGATION_NESTED);
  assertThrows(org.springframework.dao.DataIntegrityViolationException.class,()->nested.execute(status->failing.review(t.admin,a.attendanceId(),true,t.service.detail(t.admin,false,sid).row().version(),"Đã kiểm tra")));
  assertFalse(t.service.detail(t.admin,false,sid).row().checkInReviewed());
 });}

 @Test void confirmedAttendanceCannotSubmitNewAdjustment(){t.rollback(()->{
  long sid=t.fixture(t.employee,1);var a=t.manual(sid,t.employee,1);t.service.verify(t.admin,a.attendanceId(),true,null);
  assertFalse(t.service.detail(t.employee,true,sid).row().canRequest());
  var error=assertThrows(ResponseStatusException.class,()->t.service.submit(t.employee,t.proposal(t.attendance.get(a.attendanceId()),"CONFIRMED_CORRECTION")));
  assertTrue(error.getReason().contains("Công đã được xác nhận"));
 });}
}
