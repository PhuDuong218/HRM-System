package com.hrm.service;

import java.time.*;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import static com.hrm.dto.WorkTimeDTOs.*;

@Service
public class WorkShiftService {
 private final JdbcTemplate db;
 public WorkShiftService(JdbcTemplate db){this.db=db;}
 private static final RowMapper<ShiftView> MAPPER=(r,n)->new ShiftView(
   r.getLong("shift_id"),r.getString("shift_code"),r.getString("shift_name"),
   r.getTime("start_time").toLocalTime(),r.getTime("end_time").toLocalTime(),
   r.getInt("end_day_offset"),r.getInt("break_minutes"),r.getBoolean("is_active"));
 public List<ShiftView> list(){return db.query("SELECT * FROM dbo.work_shifts ORDER BY shift_id",MAPPER);}
 public ShiftView get(long id){return db.query("SELECT * FROM dbo.work_shifts WHERE shift_id=?",MAPPER,id).stream()
   .findFirst().orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"Không tìm thấy ca"));}
 private void validate(ShiftInput v){
   if(v.startTime()==null||v.endTime()==null||v.shiftCode()==null||v.shiftCode().isBlank()||v.shiftName()==null||v.shiftName().isBlank())
     throw new IllegalArgumentException("Thiếu mã, tên hoặc thời gian ca");
   long mins=java.time.Duration.between(v.startTime(),v.endTime()).toMinutes()+1440L*v.endDayOffset();
   if(v.endDayOffset()<0||v.endDayOffset()>1||mins<=0||v.breakMinutes()<0||v.breakMinutes()>mins)
     throw new IllegalArgumentException("Thời lượng ca hoặc phút nghỉ không hợp lệ");
 }
 @Transactional
 public ShiftView create(ShiftInput v){
   validate(v);
   Long id=db.queryForObject("INSERT INTO dbo.work_shifts(shift_code,shift_name,start_time,end_time,end_day_offset,break_minutes,is_active) OUTPUT INSERTED.shift_id VALUES (?,?,?,?,?,?,?)",
    Long.class,v.shiftCode().trim(),v.shiftName().trim(),v.startTime(),v.endTime(),v.endDayOffset(),v.breakMinutes(),v.active());
   return get(id);
 }
 @Transactional
 public ShiftView update(long id,ShiftInput v){
   validate(v);
   int count=db.update("UPDATE dbo.work_shifts SET shift_code=?,shift_name=?,start_time=?,end_time=?,end_day_offset=?,break_minutes=?,is_active=? WHERE shift_id=?",
    v.shiftCode().trim(),v.shiftName().trim(),v.startTime(),v.endTime(),v.endDayOffset(),v.breakMinutes(),v.active(),id);
   if(count==0) throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Không tìm thấy ca");
   return get(id);
 }
}
