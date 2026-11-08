package com.hrm.service;
import java.time.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/** Optional daily grant job. Repeated runs preserve manual grants/adjustments. */
@Service
public class LeaveAccrualRunner {
 private final JdbcTemplate db;private final LeaveAccrualService grants;private final PersonnelWorkflowSupport support;private final boolean enabled;
 private static final org.slf4j.Logger LOG=org.slf4j.LoggerFactory.getLogger(LeaveAccrualRunner.class);
 public LeaveAccrualRunner(JdbcTemplate db,LeaveAccrualService grants,PersonnelWorkflowSupport support,@Value("${personnel.leave-accrual.scheduler-enabled:false}") boolean enabled){this.db=db;this.grants=grants;this.support=support;this.enabled=enabled;}
 @Scheduled(cron="${personnel.leave-accrual.cron:0 15 0 * * *}",zone="Asia/Ho_Chi_Minh")
 public void scheduled(){
  if(!enabled)return;
  if(db.queryForObject("SELECT CASE WHEN COL_LENGTH('dbo.personnel_leave_accrual_policies','department_id') IS NULL THEN 0 ELSE 1 END",Integer.class)!=1){LOG.warn("Leave accrual skipped: migrations 009/013 required");return;}
  LocalDate start=support.today().withDayOfYear(1),end=start.plusYears(1);
  var policies=db.queryForList("SELECT p.leave_type_id,a.username FROM dbo.personnel_leave_accrual_policies p JOIN dbo.accounts a ON a.account_id=p.updated_by JOIN dbo.roles r ON r.role_id=a.role_id JOIN dbo.employees e ON e.employee_id=a.employee_id WHERE a.is_active=1 AND r.role_code='ADMIN' AND e.employment_status='DANG_LAM' ORDER BY CASE WHEN p.department_id IS NOT NULL THEN 0 ELSE 1 END,p.leave_type_id");
  for(var row:policies){long type=((Number)row.get("leave_type_id")).longValue();String username=row.get("username").toString();try{grants.generate(type,new LeaveAccrualService.Run(start,end,"Cấp phép tự động theo chính sách đã cấu hình; phân bổ ngày lịch"),()->username);}catch(RuntimeException e){LOG.warn("Leave accrual type {} blocked: {}",type,e.getMessage());}}
 }
}
