package com.hrm.service;
import java.time.*;import java.util.*;import com.hrm.security.AuthenticatedAccount;import com.hrm.dto.PageResponse;import org.springframework.jdbc.core.JdbcTemplate;import org.springframework.stereotype.Service;import org.springframework.transaction.annotation.Transactional;import org.springframework.http.HttpStatus;import org.springframework.web.server.ResponseStatusException;
@Service public class DepartmentWorkspaceService {
 @org.springframework.beans.factory.annotation.Autowired private ScheduleStateWriteService scheduleStates;
 private final JdbcTemplate db;private final DepartmentWorkspaceAccess access;private final ScheduleWorkspaceService schedules;private final ConfirmedAttendancePeriodService summary;private final DepartmentReviewService reviews;
 public DepartmentWorkspaceService(JdbcTemplate db,DepartmentWorkspaceAccess access,ScheduleWorkspaceService schedules,ConfirmedAttendancePeriodService summary,DepartmentReviewService reviews){this.db=db;this.access=access;this.schedules=schedules;this.summary=summary;this.reviews=reviews;}
 private static final String TODAY="CONVERT(date,SYSDATETIMEOFFSET() AT TIME ZONE 'SE Asia Standard Time')";
 public Map<String,Object> options(AuthenticatedAccount p){access.departments(p);String scope=access.predicate(p,"e.employee_id",TODAY);return Map.of("employees",db.queryForList("SELECT e.employee_id employeeId,e.employee_code employeeCode,e.full_name fullName FROM dbo.employees e WHERE e.employment_status='DANG_LAM' AND "+scope+" ORDER BY e.full_name"),"shifts",db.queryForList("SELECT shift_id shiftId,shift_code shiftCode,shift_name shiftName,start_time startTime,end_time endTime,end_day_offset endDayOffset,break_minutes breakMinutes FROM dbo.work_shifts WHERE is_active=1 ORDER BY shift_code"));}
 public PageResponse<Map<String,Object>> list(AuthenticatedAccount p,String kind,LocalDate from,LocalDate to,String q,String status,Long employee,int page){
  access.departments(p);if(from==null||to==null||from.isAfter(to)||to.isAfter(from.plusDays(366))||page<0||q!=null&&q.length()>200)throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Khoảng ngày tối đa 366 ngày; bộ lọc không hợp lệ");
  String select,base,date,id,emp,sort;switch(kind){
   case "assignments"->{select="a.assignment_id id,e.employee_id employeeId,e.employee_code employeeCode,e.full_name fullName,d.department_name department,p.position_name position,a.effective_from effectiveFrom,a.effective_to effectiveTo";base=" FROM dbo.employee_assignments a JOIN dbo.employees e ON e.employee_id=a.employee_id JOIN dbo.departments d ON d.department_id=a.department_id JOIN dbo.positions p ON p.position_id=a.position_id WHERE a.effective_from<=? AND (a.effective_to IS NULL OR a.effective_to>?)";date=TODAY;emp="e.employee_id";id="a.assignment_id";sort=id+" DESC";}
   case "schedules","attendance"->{select="s.schedule_id id,e.employee_id employeeId,e.employee_code employeeCode,e.full_name fullName,CONCAT(w.shift_code,N' - ',w.shift_name) shift,s.shift_id shiftId,s.work_date workDate,s.planned_start plannedStart,s.planned_end plannedEnd,s.planned_break_minutes breakMinutes,s.note,CASE WHEN s.cancelled_at IS NOT NULL THEN 'CANCELLED' ELSE 'ACTIVE' END scheduleStatus";base=" FROM dbo.work_schedules s JOIN dbo.employees e ON e.employee_id=s.employee_id LEFT JOIN dbo.work_shifts w ON w.shift_id=s.shift_id LEFT JOIN dbo.attendance_records ar ON ar.schedule_id=s.schedule_id AND ar.invalidated_at IS NULL WHERE s.work_date BETWEEN ? AND ?";if(kind.equals("attendance"))select+=",ar.attendance_id attendanceId,ar.actual_start actualStart,ar.actual_end actualEnd,ar.attendance_result result,COALESCE(ar.verification_status,'CHUA_CO_CONG') status,ar.check_in_reviewed_at checkInReviewedAt,ar.check_out_reviewed_at checkOutReviewedAt";date="s.work_date";emp="s.employee_id";id="s.schedule_id";sort="s.work_date DESC,"+id+" DESC";}
   case "overtime"->{select="o.overtime_id id,e.employee_id employeeId,e.employee_code employeeCode,e.full_name fullName,o.request_code code,o.requested_start plannedStart,o.requested_end plannedEnd,o.actual_start actualStart,o.actual_end actualEnd,o.status,o.reason,o.submitted_at submittedAt,o.processed_at processedAt,o.confirmed_at confirmedAt";base=" FROM dbo.overtime_requests o JOIN dbo.employees e ON e.employee_id=o.employee_id WHERE CAST(o.requested_start AS date) BETWEEN ? AND ?";date="CAST(o.requested_start AS date)";emp="o.employee_id";id="o.overtime_id";sort="o.submitted_at DESC,"+id+" DESC";}
   case "reviews" -> throw new ResponseStatusException(HttpStatus.FORBIDDEN,"Trưởng phòng chỉ duyệt đơn nghỉ phép và nghỉ việc.");
   default->throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Mục tra cứu không hợp lệ");
  }
  var args=new ArrayList<Object>();if(kind.equals("assignments")){args.add(to);args.add(from);}else{args.add(from);args.add(to);}
  base+=" AND "+access.predicate(p,emp,date);
  if(kind.equals("assignments"))base+=" AND a.department_id IN ("+access.departments(p).stream().map(String::valueOf).collect(java.util.stream.Collectors.joining(","))+")";
  if(employee!=null){base+=" AND "+emp+"=?";args.add(employee);}
  if(q!=null&&!q.isBlank()){String normalized=java.text.Normalizer.normalize(q.trim(),java.text.Normalizer.Form.NFD).replaceAll("\\p{M}+","").replace('đ','d').replace('Đ','D');String text="%"+normalized.replace("[","[[]").replace("%","[%]").replace("_","[_]")+"%";base+=" AND (e.employee_code COLLATE Latin1_General_100_CI_AI LIKE ? OR REPLACE(REPLACE(e.full_name,N'đ',N'd'),N'Đ',N'D') COLLATE Latin1_General_100_CI_AI LIKE ?)";args.add(text);args.add(text);}
  if(status!=null&&!status.isBlank()){if(kind.equals("reviews")){base+=" AND COALESCE(review.decision,'PENDING')=?";args.add(status);}else if(kind.equals("attendance")){base+=" AND COALESCE(ar.verification_status,'CHUA_CO_CONG')=?";args.add(status);}else if(kind.equals("overtime")){base+=" AND o.status=?";args.add(status);}}
  long total=db.queryForObject("SELECT COUNT_BIG(*)"+base,Long.class,args.toArray());args.add((long)page*10);args.add(10);
  return new PageResponse<>(db.queryForList("SELECT "+select+base+" ORDER BY "+sort+" OFFSET ? ROWS FETCH NEXT ? ROWS ONLY",args.toArray()),page,10,total,(int)((total+9)/10));
 }
 public record CalendarLeave(LocalDate day, boolean maternity) {}

 public List<CalendarLeave> calendarLeave(AuthenticatedAccount principal, long employeeId, LocalDate from, LocalDate to) {
  if (employeeId <= 0 || from.isAfter(to) || to.isAfter(from.plusDays(41))) {
   throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Khoảng lịch không hợp lệ.");
  }
  String scope = access.predicate(principal, "r.employee_id", "d.day");
  return db.query("""
      WITH dates AS (
        SELECT CAST(? AS date) day
        UNION ALL SELECT DATEADD(day,1,day) FROM dates WHERE day<?
      )
      SELECT d.day, MAX(CASE WHEN t.type_code IN ('MATERNITY','THAI_SAN') THEN 1 ELSE 0 END) maternity
      FROM dates d
      JOIN dbo.leave_request_details l ON l.leave_start<DATEADD(day,1,CAST(d.day AS datetime2))
        AND l.leave_end>CAST(d.day AS datetime2)
      JOIN dbo.employee_requests r ON r.request_id=l.request_id
      JOIN dbo.leave_types t ON t.leave_type_id=l.leave_type_id
      WHERE r.employee_id=? AND r.status='DA_DUYET'
        AND NOT EXISTS (SELECT 1 FROM dbo.employee_request_withdrawals w WHERE w.request_id=r.request_id)
        AND """ + " " + scope + " GROUP BY d.day ORDER BY d.day",
      (rs, row) -> new CalendarLeave(rs.getObject(1, LocalDate.class), rs.getBoolean(2)), from, to, employeeId);
 }

 public Object summary(AuthenticatedAccount p,LocalDate from,LocalDate to,Long employee){return summary.summarizeDepartment(p,from,to,employee);}
 public Map<String,Object> personnel(AuthenticatedAccount p,long employee){access.employee(p,employee,access.today(),false);return Map.of("qualifications",db.queryForList("SELECT level.level_name qualification,eq.major,eq.institution,eq.graduation_year graduationYear,eq.certificate_number certificateNumber FROM dbo.employee_qualifications eq JOIN dbo.qualification_levels level ON level.qualification_level_id=eq.qualification_level_id WHERE eq.employee_id=? AND eq.is_deleted=0",employee),"contracts",db.queryForList("SELECT contract_number contractNumber,start_date startDate,end_date endDate,status FROM dbo.contracts WHERE employee_id=? ORDER BY start_date DESC",employee));}
 public Map<String,Object> detail(AuthenticatedAccount p,String kind,long id){
  if(kind.equals("overtime")){var row=reviews.source("OT",id);access.employee(p,((Number)row.get("employee_id")).longValue(),((java.sql.Timestamp)row.get("starts")).toLocalDateTime().toLocalDate(),false);return Map.of("plannedBreaks",db.queryForList("SELECT break_start starts,break_end ends FROM dbo.overtime_requested_breaks WHERE overtime_id=? AND is_active=1 ORDER BY break_start",id),"actualBreaks",db.queryForList("SELECT break_start starts,break_end ends FROM dbo.overtime_breaks WHERE overtime_id=? AND is_active=1 ORDER BY break_start",id),"history",db.queryForList("SELECT l.occurred_at occurredAt,l.action,l.reason,a.username actor FROM dbo.audit_logs l LEFT JOIN dbo.accounts a ON a.account_id=l.actor_account_id WHERE l.target_table='overtime_requests' AND l.target_record_key=? ORDER BY l.audit_id DESC",Long.toString(id)));}
  if(kind.equals("reviews"))throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Cần loại OT hoặc ATTENDANCE");
  if(kind.equals("schedules")||kind.equals("attendance")){var row=db.queryForMap("SELECT employee_id,work_date FROM dbo.work_schedules WHERE schedule_id=?",id);access.employee(p,((Number)row.get("employee_id")).longValue(),((java.sql.Date)row.get("work_date")).toLocalDate(),false);return Map.of("breaks",db.queryForList("SELECT break_start starts,break_end ends FROM dbo.work_schedule_breaks WHERE schedule_id=? AND superseded_at IS NULL ORDER BY break_start",id),"history",db.queryForList("SELECT occurred_at occurredAt,action,reason FROM dbo.audit_logs WHERE (target_table='work_schedules' AND target_record_key=?) OR (target_table='attendance_records' AND target_record_key IN(SELECT CONVERT(varchar(30),attendance_id) FROM dbo.attendance_records WHERE schedule_id=?)) ORDER BY occurred_at DESC",Long.toString(id),id));}
  throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Mục không hỗ trợ chi tiết");
 }
 @Transactional("scheduleTransactionManager") public Object cancel(AuthenticatedAccount p,long id,String reason){var row=db.queryForMap("SELECT employee_id,work_date FROM dbo.work_schedules WHERE schedule_id=?",id);access.employee(p,((Number)row.get("employee_id")).longValue(),((java.sql.Date)row.get("work_date")).toLocalDate(),true);return scheduleStates.cancel(p,id,reason);}
 @Transactional("scheduleTransactionManager") public Object save(AuthenticatedAccount p,Long id,ScheduleWorkspaceService.SaveInput body){access.employee(p,body.employeeId(),body.workDate(),true);return schedules.save(p,id,body);}
}
