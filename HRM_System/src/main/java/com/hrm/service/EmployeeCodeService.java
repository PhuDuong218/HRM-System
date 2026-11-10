package com.hrm.service;

import com.hrm.dto.AuditEvent;
import com.hrm.model.Employee;
import com.hrm.repository.EmployeeRepository;
import java.time.*;
import java.util.*;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

@Service
public class EmployeeCodeService {
 private final JdbcTemplate db; private final EmployeeRepository employees;
 private final AssignmentGuardService guard; private final AuditLogService audit; private final Clock clock;
 public EmployeeCodeService(JdbcTemplate db,EmployeeRepository employees,AssignmentGuardService guard,AuditLogService audit,@Qualifier("hrmBusinessClock") Clock clock){this.db=db;this.employees=employees;this.guard=guard;this.audit=audit;this.clock=clock;}
 public static String prefix(String position){return switch(position){case "TV","TVV"->"TVV";case "GV"->"GV";case "TRUONG_PHONG","TP"->"TP";case "HR","NS"->"NS";case "KT"->"KT";case "IT","KTV"->"KTV";case "ADMIN_SYS","QT"->"QT";default->throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Chức vụ chưa có quy tắc cấp mã nhân viên: "+position);};}
 @Transactional(propagation=Propagation.MANDATORY)
 public String next(String position){
  guard.lockTimeline();String prefix=prefix(position);
  var numbers=db.queryForList("UPDATE dbo.employee_code_counters WITH(UPDLOCK,HOLDLOCK) SET last_number=last_number+1 OUTPUT inserted.last_number WHERE prefix=? AND last_number<99999",Integer.class,prefix);
  if(numbers.size()!=1)throw new ResponseStatusException(HttpStatus.CONFLICT,"Chưa cấu hình hoặc đã hết số cấp mã cho chức vụ "+position);
  String code=prefix+String.format(java.util.Locale.ROOT,"%05d",numbers.get(0));
  while(Boolean.TRUE.equals(db.queryForObject("SELECT CASE WHEN EXISTS(SELECT 1 FROM dbo.employees WHERE employee_code=?) THEN CAST(1 AS bit) ELSE CAST(0 AS bit) END",Boolean.class,code))) {
   numbers=db.queryForList("UPDATE dbo.employee_code_counters WITH(UPDLOCK,HOLDLOCK) SET last_number=last_number+1 OUTPUT inserted.last_number WHERE prefix=? AND last_number<99999",Integer.class,prefix);
   if(numbers.size()!=1)throw new ResponseStatusException(HttpStatus.CONFLICT,"Đã hết số cấp mã");
   code=prefix+String.format(java.util.Locale.ROOT,"%05d",numbers.get(0));
  }
  return code;
 }
 @Transactional(propagation=Propagation.MANDATORY)
 public void track(Long employee,Long position){
  guard.lockTimeline();db.update("IF NOT EXISTS(SELECT 1 FROM dbo.employee_code_state WITH(UPDLOCK,HOLDLOCK) WHERE employee_id=?) INSERT dbo.employee_code_state(employee_id,position_id) VALUES(?,?)",employee,employee,position);
 }
 private record Current(long position,String code,String name,LocalDate from,Long actor){}
 @Transactional(propagation=Propagation.MANDATORY)
 public void sync(Employee employee,Long actor,String reason){
  guard.lockTimeline();LocalDate today=LocalDate.now(clock);
  var current=db.query("SELECT a.position_id,p.position_code,p.position_name,a.effective_from,a.created_by FROM dbo.employee_assignments a JOIN dbo.positions p ON p.position_id=a.position_id WHERE a.employee_id=? AND a.effective_from<=? AND (a.effective_to IS NULL OR a.effective_to>?)",(rs,n)->new Current(rs.getLong(1),rs.getString(2),rs.getString(3),rs.getObject(4,LocalDate.class),rs.getObject(5,Long.class)),employee.getEmployeeId(),today,today);
  if(current.size()!=1)throw new ResponseStatusException(HttpStatus.CONFLICT,"Nhân viên phải có đúng một chức vụ đang hiệu lực");
  var c=current.get(0);var previous=db.queryForList("SELECT position_id FROM dbo.employee_code_state WHERE employee_id=?",Long.class,employee.getEmployeeId());
  if(previous.isEmpty()){track(employee.getEmployeeId(),c.position());return;}
  long old=previous.get(0);if(old==c.position())return;
  String oldName=db.queryForObject("SELECT position_name FROM dbo.positions WHERE position_id=?",String.class,old);
  String oldCode=employee.getEmployeeCode(),code=next(c.code());
  employee.setEmployeeCode(code);employee.setUpdatedBy(actor!=null?actor:c.actor());employee.setUpdatedAt(LocalDateTime.now(clock.withZone(ZoneOffset.UTC)));employees.saveAndFlush(employee);
  db.update("UPDATE dbo.employee_code_state SET position_id=? WHERE employee_id=?",c.position(),employee.getEmployeeId());
  audit.record(new AuditEvent(actor!=null?actor:c.actor(),"EMPLOYEE_POSITION_CODE_CHANGED","employees",employee.getEmployeeId().toString(),reason==null?"Đổi mã theo chức vụ có hiệu lực":reason,
    Map.of("employeeCode",oldCode,"positionId",old,"positionName",oldName),Map.of("employeeCode",code,"positionId",c.position(),"positionName",c.name(),"effectiveFrom",c.from().toString())));
 }
 @Transactional
 public void syncDue(){
  guard.lockTimeline();LocalDate today=LocalDate.now(clock);
  var ids=db.queryForList("SELECT DISTINCT e.employee_id FROM dbo.employee_code_state st JOIN dbo.employees e ON e.employee_id=st.employee_id JOIN dbo.employee_assignments a ON a.employee_id=e.employee_id WHERE e.employment_status='DANG_LAM' AND a.position_id<>st.position_id AND a.effective_from<=? AND (a.effective_to IS NULL OR a.effective_to>?)",Long.class,today,today);
  for(Long id:ids)sync(employees.findByIdForUpdate(id).orElseThrow(),null,"Tự đổi mã khi chức vụ mới bắt đầu có hiệu lực");
 }
}
