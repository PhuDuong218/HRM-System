package com.hrm.service;

import com.hrm.dto.AttendanceDTOs.AttendanceCorrection;
import com.hrm.security.AuthenticatedAccount;
import java.time.*;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AttendanceActualBreakService {
 public record Break(LocalDateTime start,LocalDateTime end) {}
 public record Save(String expectedVersion,List<Break> breaks,String reason) {}
 public record Detail(boolean verified,List<Break> breaks) {}
 private record Stored(Break rest,LocalDateTime sourceStart,LocalDateTime sourceEnd,int minutes) {}
 private final JdbcTemplate db;private final AttendancePortalService portal;
 public AttendanceActualBreakService(JdbcTemplate db,AttendancePortalService portal){this.db=db;this.portal=portal;}
 private boolean installed(){return db.queryForObject("SELECT CASE WHEN OBJECT_ID('dbo.attendance_actual_breaks','U') IS NULL THEN 0 ELSE 1 END",Integer.class)==1;}
 public record Source(long id,LocalDateTime start,LocalDateTime end,Integer minutes) {}
 public Map<Long,List<OvertimeTimeMath.Span>> verifiedMany(List<Source> sources){
  Map<Long,List<OvertimeTimeMath.Span>> result=new HashMap<>();
  if(sources.isEmpty()||!installed())return result;
  Map<Long,List<Stored>> byAttendance=new HashMap<>();
  var ids=sources.stream().map(Source::id).distinct().toList();
  for(int offset=0;offset<ids.size();offset+=500){
   var chunk=ids.subList(offset,Math.min(offset+500,ids.size()));
   String placeholders=String.join(",",Collections.nCopies(chunk.size(),"?"));
   var rows=db.query("SELECT attendance_id,break_start,break_end,source_start,source_end,source_break_minutes FROM dbo.attendance_actual_breaks WHERE attendance_id IN ("+placeholders+") AND superseded_at IS NULL ORDER BY attendance_id,break_start",(rs,n)->Map.entry(rs.getLong(1),new Stored(new Break(rs.getObject(2,LocalDateTime.class),rs.getObject(3,LocalDateTime.class)),rs.getObject(4,LocalDateTime.class),rs.getObject(5,LocalDateTime.class),rs.getInt(6))),chunk.toArray());
   for(var row:rows)byAttendance.computeIfAbsent(row.getKey(),key->new ArrayList<>()).add(row.getValue());
  }
  for(var source:sources){var rows=byAttendance.get(source.id());if(rows!=null&&!rows.isEmpty()&&rows.stream().allMatch(r->Objects.equals(source.start(),r.sourceStart())&&Objects.equals(source.end(),r.sourceEnd())&&Objects.equals(source.minutes(),r.minutes())))result.put(source.id(),rows.stream().map(r->new OvertimeTimeMath.Span(r.rest().start(),r.rest().end())).toList());}
  return result;
 }
 public List<OvertimeTimeMath.Span> verified(long id,LocalDateTime start,LocalDateTime end,Integer minutes){return verifiedMany(List.of(new Source(id,start,end,minutes))).get(id);}
 @Transactional(value="scheduleTransactionManager",readOnly=true)
 public Detail detail(AuthenticatedAccount actor,boolean mine,long schedule){
  var row=portal.detail(actor,mine,schedule).row();
  var rests=row.attendanceId()==null?null:verified(row.attendanceId(),row.actualStart(),row.actualEnd(),row.actualBreakMinutes());
  return new Detail(rests!=null||row.attendanceId()!=null&&row.actualBreakMinutes()==0,rests==null?List.of():rests.stream().map(r->new Break(r.start(),r.end())).toList());
 }
 @Transactional("scheduleTransactionManager")
 public Detail save(AuthenticatedAccount actor,long id,Save body){
  OvertimeTimeMath.require(installed(),"Apply migration 012 before saving actual breaks");
  OvertimeTimeMath.require(body!=null&&body.reason()!=null&&!body.reason().isBlank()&&body.reason().length()<=1000&&body.breaks()!=null,"Actual break reason and intervals are required");
  var ids=db.query("SELECT employee_id,schedule_id FROM dbo.attendance_records WHERE attendance_id=? AND invalidated_at IS NULL",(rs,n)->new long[]{rs.getLong(1),rs.getLong(2)},id);
  OvertimeTimeMath.require(ids.size()==1,"Attendance not found");
  db.queryForObject("SELECT employee_id FROM dbo.employees WITH(UPDLOCK,HOLDLOCK) WHERE employee_id=?",Long.class,ids.get(0)[0]);
  db.queryForObject("SELECT attendance_id FROM dbo.attendance_records WITH(UPDLOCK,HOLDLOCK) WHERE attendance_id=?",Long.class,id);
  var row=portal.detail(actor,false,ids.get(0)[1]).row();
  OvertimeTimeMath.require(Objects.equals(row.attendanceId(),id)&&row.canEdit()&&"CHUA_XAC_NHAN".equals(row.verificationStatus()),"Only unconfirmed attendance in your management scope can be reconciled");
  OvertimeTimeMath.require(body.expectedVersion()!=null&&body.expectedVersion().equals(row.version()),"Attendance changed; reload before saving");
  OvertimeTimeMath.require(body.breaks().stream().allMatch(Objects::nonNull),"Invalid actual break");
  var rests=body.breaks().stream().map(r->new OvertimeTimeMath.Span(r.start(),r.end())).toList();
  int minutes=Math.toIntExact(OvertimeTimeMath.breaks(new OvertimeTimeMath.Span(row.actualStart(),row.actualEnd()),rests).toMinutes());
  String before=db.queryForObject("SELECT (SELECT break_start,break_end FROM dbo.attendance_actual_breaks WHERE attendance_id=? AND superseded_at IS NULL FOR JSON PATH)",String.class,id);
  portal.correct(actor,id,new AttendanceCorrection(row.actualStart(),row.actualEnd(),minutes,row.attendanceResult(),body.reason().trim()));
  db.update("UPDATE dbo.attendance_actual_breaks SET superseded_at=SYSUTCDATETIME() WHERE attendance_id=? AND superseded_at IS NULL",id);
  for(var rest:rests)db.update("INSERT dbo.attendance_actual_breaks(attendance_id,break_start,break_end,source_start,source_end,source_break_minutes,created_by) VALUES(?,?,?,?,?,?,?)",id,rest.start(),rest.end(),row.actualStart(),row.actualEnd(),minutes,actor.accountId());
  String after=db.queryForObject("SELECT (SELECT break_start,break_end FROM dbo.attendance_actual_breaks WHERE attendance_id=? AND superseded_at IS NULL FOR JSON PATH)",String.class,id);
  db.update("INSERT dbo.audit_logs(actor_account_id,action,target_table,target_record_key,reason,before_data_json,after_data_json) VALUES(?,'ATTENDANCE_ACTUAL_BREAKS','attendance_records',?,?,?,?)",actor.accountId(),Long.toString(id),body.reason().trim(),before,after);
  return detail(actor,false,row.scheduleId());
 }
}
