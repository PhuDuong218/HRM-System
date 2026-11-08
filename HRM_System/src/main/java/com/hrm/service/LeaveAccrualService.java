package com.hrm.service;

import java.math.*;
import java.security.Principal;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;
import jakarta.validation.constraints.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Configured center policy: calendar-day proration, explicit hours/day, four-decimal rounding. */
@Service
public class LeaveAccrualService {
 public record Policy(@Min(1) @Max(366) int annualDays,@DecimalMin("0.0001") @DecimalMax("24") BigDecimal hoursPerDay,boolean seniorityExtra,@Positive Long departmentId){
  public Policy(int days,BigDecimal hours,boolean extra){this(days,hours,extra,null);}
 }
 public record Run(@NotNull LocalDate periodStart,@NotNull LocalDate periodEnd,@NotBlank @Size(max=1000) String reason,Long departmentId){public Run(LocalDate start,LocalDate end,String reason){this(start,end,reason,null);}}
 @org.springframework.beans.factory.annotation.Autowired private LeaveRequestRules rules;
 private final JdbcTemplate db;private final PersonnelAccessService access;private final PersonnelScopeService scope;private final PersonnelWorkflowSupport support;
 public LeaveAccrualService(JdbcTemplate db,PersonnelAccessService access,PersonnelScopeService scope,PersonnelWorkflowSupport support){this.db=db;this.access=access;this.scope=scope;this.support=support;}
 private void requireSchema(){if(db.queryForObject("SELECT CASE WHEN COL_LENGTH('dbo.personnel_leave_accrual_policies','department_id') IS NULL THEN 0 ELSE 1 END",Integer.class)!=1)throw PersonnelWorkflowSupport.conflict("Cần migrations 009 và 013 để cấp phép theo chính sách");}
 public Map<String,Object> get(long type,Principal p){access.manager(p);requireSchema();var rows=db.queryForList("SELECT annual_days AS annualDays,hours_per_day AS hoursPerDay,seniority_extra AS seniorityExtra,department_id AS departmentId FROM dbo.personnel_leave_accrual_policies WHERE leave_type_id=?",type);return rows.isEmpty()?Map.of("configured",false):rows.get(0);}
 @Transactional public Policy save(long type,Policy policy,Principal p){
  var actor=access.admin(p);requireSchema();support.lock();
  if(policy.annualDays()<1||policy.annualDays()>366||policy.hoursPerDay()==null||policy.hoursPerDay().signum()<=0||policy.hoursPerDay().compareTo(BigDecimal.valueOf(24))>0||policy.hoursPerDay().scale()>4)throw PersonnelWorkflowSupport.bad("Ngày phép và số giờ/ngày không hợp lệ (tối đa 4 số lẻ)");
  long usable=db.queryForObject("SELECT COUNT_BIG(*) FROM dbo.leave_types l JOIN dbo.g2_leave_policies p ON p.leave_type_id=l.leave_type_id WHERE l.leave_type_id=? AND l.is_active=1 AND p.consumes_entitlement=1",Long.class,type);
  if(usable!=1)throw PersonnelWorkflowSupport.conflict("Chỉ cấu hình cấp phép cho loại nghỉ đang hoạt động và có trừ quỹ");
  if(policy.departmentId()!=null&&db.queryForObject("SELECT COUNT_BIG(*) FROM dbo.departments WHERE department_id=? AND is_active=1",Long.class,policy.departmentId())!=1)throw PersonnelWorkflowSupport.bad("Phòng áp dụng không hợp lệ");
  var before=get(type,p);
  boolean present=db.queryForObject("SELECT COUNT_BIG(*) FROM dbo.personnel_leave_accrual_policies WHERE leave_type_id=?",Long.class,type)>0;
  if(present)db.update("UPDATE dbo.personnel_leave_accrual_policies SET annual_days=?,hours_per_day=?,seniority_extra=?,updated_by=?,updated_at=SYSUTCDATETIME() WHERE leave_type_id=?",policy.annualDays(),policy.hoursPerDay(),policy.seniorityExtra(),actor.accountId(),type);
  else db.update("INSERT dbo.personnel_leave_accrual_policies(leave_type_id,annual_days,hours_per_day,seniority_extra,updated_by) VALUES(?,?,?,?,?)",type,policy.annualDays(),policy.hoursPerDay(),policy.seniorityExtra(),actor.accountId());
  db.update("UPDATE dbo.personnel_leave_accrual_policies SET department_id=? WHERE leave_type_id=?",policy.departmentId(),type);
  support.audit(actor.accountId(),"LEAVE_ACCRUAL_POLICY","leave_types",type,"Cấu hình chính sách cấp phép",before,policy);return policy;
 }
 public static BigDecimal calculate(Policy policy,LocalDate hire,LocalDate start,LocalDate end){
  return calculate(policy,hire,start,end,hire);
 }
 public static BigDecimal calculate(Policy policy,LocalDate hire,LocalDate start,LocalDate end,LocalDate eligibility){
  LocalDate from=eligibility.isAfter(start)?eligibility:start;from=hire.isAfter(from)?hire:from;if(!from.isBefore(end))return BigDecimal.ZERO.setScale(4);
  long extra=policy.seniorityExtra()?Math.max(0,ChronoUnit.YEARS.between(hire,start))/5:0;
  return BigDecimal.valueOf(policy.annualDays()+extra).multiply(policy.hoursPerDay()).multiply(BigDecimal.valueOf(ChronoUnit.DAYS.between(from,end))).divide(BigDecimal.valueOf(ChronoUnit.DAYS.between(start,end)),4,RoundingMode.HALF_UP);
 }
 @Transactional public List<Map<String,Object>> generate(long type,Run input,Principal p){
  var actor=access.manager(p);requireSchema();support.lock();
  if(input.departmentId()!=null)scope.department(input.departmentId());
  LocalDate start=input.periodStart(),end=input.periodEnd();
  if(start==null||end==null||!end.isAfter(start)||!end.equals(start.plusYears(1))||input.reason()==null||input.reason().isBlank())throw PersonnelWorkflowSupport.bad("Kỳ cấp phép phải đúng một năm [từ ngày, đến trước ngày), có lý do");
  var values=get(type,p);if(Boolean.FALSE.equals(values.get("configured")))throw PersonnelWorkflowSupport.conflict("Chưa cấu hình chính sách cấp phép");
  Policy policy=new Policy(((Number)values.get("annualDays")).intValue(),(BigDecimal)values.get("hoursPerDay"),Boolean.TRUE.equals(values.get("seniorityExtra")),values.get("departmentId")==null?null:((Number)values.get("departmentId")).longValue());
  long active=db.queryForObject("SELECT COUNT_BIG(*) FROM dbo.leave_types l JOIN dbo.g2_leave_policies g ON g.leave_type_id=l.leave_type_id WHERE l.leave_type_id=? AND l.is_active=1 AND g.consumes_entitlement=1",Long.class,type);if(active!=1)throw PersonnelWorkflowSupport.conflict("Loại nghỉ không còn phù hợp để cấp phép");
  String filter=PersonnelScopeService.isHr()?" AND "+scope.sqlEmployees("employee_id"):"";
  if(policy.departmentId()!=null)filter+=" AND EXISTS(SELECT 1 FROM dbo.employee_assignments a WHERE a.employee_id=employees.employee_id AND a.department_id="+policy.departmentId()+" AND a.effective_from<=CONVERT(date,SYSDATETIMEOFFSET() AT TIME ZONE 'SE Asia Standard Time') AND (a.effective_to IS NULL OR a.effective_to>CONVERT(date,SYSDATETIMEOFFSET() AT TIME ZONE 'SE Asia Standard Time')))";
  if(input.departmentId()!=null)filter+=" AND EXISTS(SELECT 1 FROM dbo.employee_assignments dep WHERE dep.employee_id=employees.employee_id AND dep.department_id="+input.departmentId()+" AND dep.effective_from<=CONVERT(date,SYSDATETIMEOFFSET() AT TIME ZONE 'SE Asia Standard Time') AND (dep.effective_to IS NULL OR dep.effective_to>CONVERT(date,SYSDATETIMEOFFSET() AT TIME ZONE 'SE Asia Standard Time')))";
  if(actor.role().equals("HR"))filter+=" AND employee_id<>"+actor.employeeId();
  var employees=db.queryForList("SELECT employee_id,employee_code,hire_date FROM dbo.employees WHERE employment_status='DANG_LAM' AND hire_date<?"+filter,end);
  var results=new ArrayList<Map<String,Object>>();
  for(var row:employees){long employee=((Number)row.get("employee_id")).longValue();
   long existing=db.queryForObject("SELECT COUNT_BIG(*) FROM dbo.leave_entitlements WITH(UPDLOCK,HOLDLOCK) WHERE employee_id=? AND (leave_type_id=? OR leave_type_id IN(SELECT leave_type_id FROM dbo.personnel_leave_accrual_policies)) AND period_start<? AND period_end>?",Long.class,employee,type,end,start);
   if(existing>0){results.add(Map.of("employeeCode",row.get("employee_code"),"result","SKIPPED_EXISTING"));continue;}
   LocalDate hire=((java.sql.Date)row.get("hire_date")).toLocalDate();
   BigDecimal hours=calculate(policy,hire,start,end,hire.plusMonths(rules.get(type).minimumServiceMonths()));
   if(hours.signum()<=0){results.add(Map.of("employeeCode",row.get("employee_code"),"result","SKIPPED_ZERO"));continue;}
   db.update("INSERT dbo.leave_entitlements(employee_id,leave_type_id,period_start,period_end,entitled_hours,created_by) VALUES(?,?,?,?,?,?)",employee,type,start,end,hours,actor.accountId());
   long id=db.queryForObject("SELECT entitlement_id FROM dbo.leave_entitlements WHERE employee_id=? AND leave_type_id=? AND period_start=? AND period_end=?",Long.class,employee,type,start,end);
   db.update("INSERT dbo.personnel_leave_accrual_runs(entitlement_id,employee_id,leave_type_id,period_start,period_end,granted_hours,policy_snapshot,created_by) VALUES(?,?,?,?,?,?,?,?)",id,employee,type,start,end,hours,policy.toString()+"; DAILY_PRORATA; HALF_UP_4",actor.accountId());
   support.audit(actor.accountId(),"LEAVE_ACCRUAL_GRANT","leave_entitlements",id,input.reason(),null,Map.of("hours",hours,"policy",policy,"periodStart",start,"periodEnd",end));
   results.add(Map.of("employeeCode",row.get("employee_code"),"result","GRANTED","hours",hours));
  }return results;
 }
}
