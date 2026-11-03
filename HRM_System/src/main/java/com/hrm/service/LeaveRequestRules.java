package com.hrm.service;

import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class LeaveRequestRules {
 public record Rules(int retroactiveDays,int minimumNoticeHours,BigDecimal maximumHours,int minimumServiceMonths,boolean evidenceRequired){
 public Rules(int retro,int notice,BigDecimal max){this(retro,notice,max,0,false);}
 }
 private final JdbcTemplate db;private final PayrollHistoryGuard payroll;private final PersonnelWorkflowSupport support;
 public LeaveRequestRules(JdbcTemplate db,PayrollHistoryGuard payroll,PersonnelWorkflowSupport support){this.db=db;this.payroll=payroll;this.support=support;}
 private boolean eligibility(){return db.queryForObject("SELECT CASE WHEN COL_LENGTH('dbo.personnel_leave_rules','minimum_service_months') IS NULL THEN 0 ELSE 1 END",Integer.class)==1;}
 private boolean exists(){return db.queryForObject("SELECT CASE WHEN OBJECT_ID('dbo.personnel_leave_rules') IS NULL THEN 0 ELSE 1 END",Integer.class)==1;}
 public Rules get(long type){if(!exists())return new Rules(0,0,null);var rows=db.query("SELECT retroactive_days,minimum_notice_hours,maximum_hours FROM dbo.personnel_leave_rules WHERE leave_type_id=?",(r,n)->new Rules(r.getInt(1),r.getInt(2),r.getBigDecimal(3)),type);Rules result=rows.isEmpty()?new Rules(0,0,null):rows.get(0);
 if(eligibility()&&!rows.isEmpty()){var extra=db.queryForMap("SELECT minimum_service_months,evidence_required FROM dbo.personnel_leave_rules WHERE leave_type_id=?",type);return new Rules(result.retroactiveDays(),result.minimumNoticeHours(),result.maximumHours(),((Number)extra.get("minimum_service_months")).intValue(),Boolean.TRUE.equals(extra.get("evidence_required")));}return result;}
 @Transactional public Rules save(long type,Rules rule,long actor){
  if(!exists())throw PersonnelWorkflowSupport.conflict("Cần áp migration 006 trước khi cấu hình loại nghỉ");
  if(rule.retroactiveDays()<0||rule.retroactiveDays()>7||rule.minimumNoticeHours()<0||rule.minimumNoticeHours()>8760||(rule.maximumHours()!=null&&(rule.maximumHours().signum()<=0||rule.maximumHours().scale()>4||rule.maximumHours().compareTo(new BigDecimal("99999.9999"))>0)))throw PersonnelWorkflowSupport.bad("Nộp bù tối đa 7 ngày; thời gian báo trước và hạn mức phải hợp lệ");
  Integer active=db.queryForObject("SELECT COUNT(*) FROM dbo.leave_types WHERE leave_type_id=? AND is_active=1",Integer.class,type);if(active!=1)throw PersonnelWorkflowSupport.bad("Loại nghỉ không tồn tại hoặc ngừng sử dụng");
  if(rule.minimumServiceMonths()<0||rule.minimumServiceMonths()>1200)throw PersonnelWorkflowSupport.bad("Thâm niên tối thiểu phải từ 0 đến 1200 tháng");
  if(!eligibility()&&(rule.minimumServiceMonths()>0||rule.evidenceRequired()))throw PersonnelWorkflowSupport.conflict("Cần migration 009 để cấu hình điều kiện đối tượng");
  support.lock();Rules before=get(type);
  boolean present=db.queryForObject("SELECT COUNT_BIG(*) FROM dbo.personnel_leave_rules WHERE leave_type_id=?",Long.class,type)>0;
  if(present)db.update("UPDATE dbo.personnel_leave_rules SET retroactive_days=?,minimum_notice_hours=?,maximum_hours=?,updated_by=?,updated_at=SYSUTCDATETIME() WHERE leave_type_id=?",rule.retroactiveDays(),rule.minimumNoticeHours(),rule.maximumHours(),actor,type);
  else db.update("INSERT dbo.personnel_leave_rules(leave_type_id,retroactive_days,minimum_notice_hours,maximum_hours,updated_by) VALUES(?,?,?,?,?)",type,rule.retroactiveDays(),rule.minimumNoticeHours(),rule.maximumHours(),actor);
  if(eligibility())db.update("UPDATE dbo.personnel_leave_rules SET minimum_service_months=?,evidence_required=? WHERE leave_type_id=?",rule.minimumServiceMonths(),rule.evidenceRequired(),type);
  support.audit(actor,"LEAVE_RULES_UPDATE","leave_types",type,"Cấu hình điều kiện nộp đơn",before,rule);return rule;
 }
 public void eligibility(long employee,long type,String evidence,LocalDate date){
  Rules rule=get(type);LocalDate hire=db.queryForObject("SELECT hire_date FROM dbo.employees WHERE employee_id=?",LocalDate.class,employee);
  if(hire==null||hire.plusMonths(rule.minimumServiceMonths()).isAfter(date))throw PersonnelWorkflowSupport.conflict("Chưa đủ thâm niên áp dụng loại nghỉ");
  if(rule.evidenceRequired()&&(evidence==null||evidence.isBlank()))throw PersonnelWorkflowSupport.bad("Loại nghỉ yêu cầu tham chiếu giấy tờ minh chứng");
 }
 public void evidence(long request,String reference){if(reference==null||reference.isBlank())return;if(!eligibility())throw PersonnelWorkflowSupport.conflict("Cần migration 009 để lưu minh chứng");db.update("INSERT dbo.personnel_leave_evidence(request_id,evidence_reference) VALUES(?,?)",request,reference.trim());}
 public String evidence(long request){if(!eligibility())return null;var rows=db.queryForList("SELECT evidence_reference FROM dbo.personnel_leave_evidence WHERE request_id=?",String.class,request);return rows.isEmpty()?null:rows.get(0);}
 public void validateTime(long employee,long type,LocalDateTime start,LocalDateTime end,LocalDateTime submitted){
  if(start==null||end==null||!start.isBefore(end))throw PersonnelWorkflowSupport.bad("Thời gian nghỉ phải kết thúc sau bắt đầu");
  Rules rules=get(type);LocalDateTime reference=submitted==null?support.localNow():submitted;
  if(start.isBefore(support.localNow())){
   if(start.isBefore(reference)&&(rules.retroactiveDays()==0||start.toLocalDate().isBefore(reference.toLocalDate().minusDays(rules.retroactiveDays()))))throw PersonnelWorkflowSupport.bad("Loại nghỉ không cho nộp bù hoặc vượt tối đa 7 ngày theo cấu hình");
   requireMutable(employee,start,end);
  }
  if(!start.isBefore(reference)&&Duration.between(reference,start).toHours()<rules.minimumNoticeHours())throw PersonnelWorkflowSupport.bad("Chưa đủ thời gian báo trước của loại nghỉ");
 }
 public void requireMutable(long employee,LocalDateTime start,LocalDateTime end){
  payroll.assertMutable(employee,start.toLocalDate(),end.minusNanos(1).toLocalDate());
  long confirmed=db.queryForObject("SELECT COUNT_BIG(*) FROM dbo.attendance_records WHERE employee_id=? AND work_date>=? AND work_date<=? AND verification_status='DA_XAC_NHAN' AND invalidated_at IS NULL",Long.class,employee,start.toLocalDate(),end.minusNanos(1).toLocalDate());
  if(confirmed>0)throw PersonnelWorkflowSupport.conflict("Khoảng nghỉ giao công đã xác nhận; cần quy trình điều chỉnh công");
 }
 public long netMillis(long schedule,LocalDateTime plannedStart,LocalDateTime plannedEnd,LocalDateTime from,LocalDateTime to,int pause){
  long gross=Duration.between(from,to).toMillis();
  if(pause==0)return gross;
  if(from.equals(plannedStart)&&to.equals(plannedEnd))return gross-pause*60000L;
  if(db.queryForObject("SELECT CASE WHEN OBJECT_ID('dbo.work_schedule_breaks') IS NULL THEN 0 ELSE 1 END",Integer.class)!=1)throw PersonnelWorkflowSupport.conflict("Ca chưa có vị trí khoảng nghỉ; cần hoàn thiện lịch trước khi nghỉ nửa ca");
  String verified=db.queryForObject("SELECT break_detail_status FROM dbo.work_schedules WHERE schedule_id=?",String.class,schedule);
  if(!"VERIFIED".equals(verified))throw PersonnelWorkflowSupport.conflict("Khoảng nghỉ của ca chưa được xác minh");
  var breaks=db.query("SELECT break_start,break_end FROM dbo.work_schedule_breaks WHERE schedule_id=? AND superseded_at IS NULL ORDER BY break_start",(r,n)->new LocalDateTime[]{r.getTimestamp(1).toLocalDateTime(),r.getTimestamp(2).toLocalDateTime()},schedule);
  long total=0,overlap=0;LocalDateTime previous=null;
  for(var item:breaks){var a=item[0];var b=item[1];if(!a.isBefore(b)||a.isBefore(plannedStart)||b.isAfter(plannedEnd)||(previous!=null&&a.isBefore(previous)))throw PersonnelWorkflowSupport.conflict("Dữ liệu khoảng nghỉ không hợp lệ");previous=b;total+=Duration.between(a,b).toMillis();var left=a.isAfter(from)?a:from;var right=b.isBefore(to)?b:to;if(left.isBefore(right))overlap+=Duration.between(left,right).toMillis();}
  if(total!=pause*60000L)throw PersonnelWorkflowSupport.conflict("Chi tiết giờ nghỉ không khớp tổng phút nghỉ của ca");return gross-overlap;
 }
}
