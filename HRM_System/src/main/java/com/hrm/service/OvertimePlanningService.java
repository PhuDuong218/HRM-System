package com.hrm.service;

import com.hrm.dto.OvertimeDTOs.BreakInput;
import java.time.*;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

/** Exact planned net minutes from persisted interval details; no inferred break placement. */
public class OvertimePlanningService {
    private final JdbcTemplate db;
    public OvertimePlanningService(JdbcTemplate db){this.db=db;}
    public static void require(boolean ok,String why){if(!ok)throw new ResponseStatusException(HttpStatus.CONFLICT,why);}
    public static List<BreakInput> validate(LocalDateTime start,LocalDateTime end,List<BreakInput> breaks){
        require(start!=null&&end!=null&&end.isAfter(start),"Khoảng OT không hợp lệ.");
        require(start.getSecond()==0&&start.getNano()==0&&end.getSecond()==0&&end.getNano()==0,"Giờ OT phải theo phút.");
        var sorted=new ArrayList<>(breaks==null?List.<BreakInput>of():breaks);
        require(sorted.stream().allMatch(b->b!=null&&b.breakStart()!=null&&b.breakEnd()!=null),"Khoảng nghỉ thiếu thời điểm.");sorted.sort(Comparator.comparing(BreakInput::breakStart));LocalDateTime last=start;
        for(var b:sorted){require(!b.breakStart().isBefore(last)&&b.breakEnd().isAfter(b.breakStart())&&!b.breakEnd().isAfter(end)&&b.breakStart().getSecond()==0&&b.breakEnd().getSecond()==0&&b.breakStart().getNano()==0&&b.breakEnd().getNano()==0,"Khoảng nghỉ phải nằm trong OT, theo phút và không chồng lấn.");last=b.breakEnd();}
        require(net(start,end,sorted).values().stream().mapToLong(Long::longValue).sum()>0,"OT thuần phải lớn hơn 0 phút.");return sorted;
    }
    public static Map<LocalDate,Long> net(LocalDateTime start,LocalDateTime end,List<BreakInput> rests){
        Map<LocalDate,Long> out=new TreeMap<>();for(LocalDate d=start.toLocalDate();d.atStartOfDay().isBefore(end);d=d.plusDays(1)){
            LocalDateTime lo=start.isAfter(d.atStartOfDay())?start:d.atStartOfDay(),hi=end.isBefore(d.plusDays(1).atStartOfDay())?end:d.plusDays(1).atStartOfDay();long minutes=Duration.between(lo,hi).toMinutes();
            for(var b:rests){var a=lo.isAfter(b.breakStart())?lo:b.breakStart();var z=hi.isBefore(b.breakEnd())?hi:b.breakEnd();if(a.isBefore(z))minutes-=Duration.between(a,z).toMinutes();}out.put(d,minutes);
        }return out;
    }
    public List<BreakInput> rests(long id){return db.query("SELECT break_start,break_end FROM dbo.overtime_requested_breaks WHERE overtime_id=? AND is_active=1 ORDER BY break_start",(rs,n)->new BreakInput(rs.getObject(1,LocalDateTime.class),rs.getObject(2,LocalDateTime.class)),id);}
    public void check(long employee,LocalDateTime start,LocalDateTime end,long skip,List<BreakInput> breaks){
        var valid=validate(start,end,breaks);Long overlaps=db.queryForObject("SELECT COUNT_BIG(*) FROM dbo.overtime_requests WITH(HOLDLOCK) WHERE employee_id=? AND overtime_id<>? AND status NOT IN ('TU_CHOI','DA_HUY') AND requested_start<? AND requested_end>?",Long.class,employee,skip,end,start);require(overlaps!=null&&overlaps==0,"OT trùng khoảng đăng ký khác.");
        Long shifts=db.queryForObject("SELECT COUNT_BIG(*) FROM dbo.work_schedules WITH(HOLDLOCK) WHERE employee_id=? AND cancelled_at IS NULL AND planned_start<? AND planned_end>?",Long.class,employee,end,start);require(shifts!=null&&shifts==0,"OT chồng lấn lịch làm chuẩn.");
        Map<LocalDate,Long> totals=new TreeMap<>(net(start,end,valid));LocalDateTime lo=start.toLocalDate().atStartOfDay(),hi=end.minusNanos(1).toLocalDate().plusDays(1).atStartOfDay();
        db.query("SELECT schedule_id,planned_start,planned_end,planned_break_minutes FROM dbo.work_schedules WITH(HOLDLOCK) WHERE employee_id=? AND cancelled_at IS NULL AND planned_start<? AND planned_end>?",rs->{long sid=rs.getLong(1);var a=rs.getObject(2,LocalDateTime.class);var z=rs.getObject(3,LocalDateTime.class);var rests=db.query("SELECT break_start,break_end FROM dbo.work_schedule_breaks WHERE schedule_id=? AND superseded_at IS NULL ORDER BY break_start",(r,n)->new BreakInput(r.getObject(1,LocalDateTime.class),r.getObject(2,LocalDateTime.class)),sid);long sum=rests.stream().mapToLong(b->Duration.between(b.breakStart(),b.breakEnd()).toMinutes()).sum();require(sum==rs.getInt(4),"Lịch chuẩn chưa có đủ nghỉ chi tiết; cần đối soát trước.");net(a,z,rests).forEach((d,m)->{if(totals.containsKey(d))totals.merge(d,m,Long::sum);});},employee,hi,lo);
        db.query("SELECT overtime_id,requested_start,requested_end FROM dbo.overtime_requests WITH(HOLDLOCK) WHERE employee_id=? AND overtime_id<>? AND status IN ('CHO_DUYET','DA_DUYET') AND requested_start<? AND requested_end>?",rs->{net(rs.getObject(2,LocalDateTime.class),rs.getObject(3,LocalDateTime.class),rests(rs.getLong(1))).forEach((d,m)->{if(totals.containsKey(d))totals.merge(d,m,Long::sum);});},employee,skip,hi,lo);
        totals.forEach((d,m)->require(m<=720,"Tổng công chuẩn + OT vượt 12 giờ thuần ngày "+d));
    }
}
