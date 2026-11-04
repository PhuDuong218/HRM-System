package com.hrm.service;

import com.hrm.dto.OvertimeDTOs.BreakInput;
import com.hrm.security.AuthenticatedAccount;
import java.time.*;
import java.util.*;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/** Approved OT is projected as an OT schedule, never inserted as regular attendance. */
@Service
public class OvertimeCalendarService {
    private final JdbcTemplate db;private final OvertimeWorkspaceService workspace;private final OvertimePermissionService permissions;private final Clock clock;
    public OvertimeCalendarService(JdbcTemplate db,OvertimeWorkspaceService workspace,OvertimePermissionService permissions,@Qualifier("hrmBusinessClock") Clock clock){this.db=db;this.workspace=workspace;this.permissions=permissions;this.clock=clock;}
    public record Item(OvertimeWorkspaceService.Row row,boolean canCheckIn,boolean canCheckOut){}
    public record Calendar(List<Item> items,int page,int totalPages,long totalElements,LocalDateTime serverNow){}
    public record ClockInput(String expectedVersion,List<BreakInput> breaks,boolean noBreaksConfirmed){}
    private static void need(boolean condition,String message){if(!condition)throw new ResponseStatusException(HttpStatus.CONFLICT,message);}
    private LocalDateTime now(){return LocalDateTime.now(clock);}
    public Calendar calendar(AuthenticatedAccount actor,boolean mine,LocalDate from,LocalDate to,Long employee,Long department,int page){
        var result=workspace.search(actor,new OvertimeWorkspaceService.Filter(mine,from,to,null,department,"DA_DUYET",null,null,employee,page));
        var time=now();
        return new Calendar(result.items().stream().map(r->{var o=r.ot();boolean own=mine&&!"ADMIN".equals(actor.roleCode())&&Objects.equals(actor.employeeId(),o.employeeId());
            return new Item(r,own&&o.actualStart()==null&&o.confirmedBy()==null&&!time.isBefore(o.requestedStart().minusMinutes(30))&&time.isBefore(o.requestedEnd()),
                own&&o.actualStart()!=null&&o.actualEnd()==null&&o.confirmedBy()==null&&selfCheckIn(o.overtimeId()));}).toList(),result.page(),result.totalPages(),result.totalElements(),time);
    }
    private boolean selfCheckIn(long id){return Boolean.TRUE.equals(db.queryForObject("SELECT CASE WHEN (SELECT TOP(1) action FROM dbo.audit_logs WHERE target_table='overtime_requests' AND target_record_key=? AND action IN ('OT_SELF_CHECK_IN','OT_SELF_CHECK_OUT','OT_ACTUAL') ORDER BY occurred_at DESC,audit_id DESC)='OT_SELF_CHECK_IN' THEN CAST(1 AS bit) ELSE CAST(0 AS bit) END",Boolean.class,Long.toString(id)));}
    private void writable(LocalDateTime start,LocalDateTime end){
        Long count=db.queryForObject("SELECT COUNT_BIG(*) FROM dbo.payroll_periods p WITH(HOLDLOCK) JOIN dbo.payroll_batches b WITH(HOLDLOCK) ON b.period_id=p.period_id WHERE p.period_start<=? AND p.period_end>=? AND (b.locked_at IS NOT NULL OR b.published_at IS NOT NULL OR b.status='DA_CHOT')",Long.class,end.minusNanos(1).toLocalDate(),start.toLocalDate());
        need(count!=null&&count==0,"Kỳ công/lương đã khóa.");
    }
    private void noOverlap(long employee,long id,LocalDateTime start,LocalDateTime end){
        Long regular=db.queryForObject("SELECT COUNT_BIG(*) FROM dbo.attendance_records WITH(HOLDLOCK) WHERE employee_id=? AND invalidated_at IS NULL AND actual_start<? AND (actual_end IS NULL OR actual_end>?)",Long.class,employee,end,start);
        Long ot=db.queryForObject("SELECT COUNT_BIG(*) FROM dbo.overtime_requests WITH(HOLDLOCK) WHERE employee_id=? AND overtime_id<>? AND status='DA_DUYET' AND actual_start<? AND (actual_end IS NULL OR actual_end>?)",Long.class,employee,id,end,start);
        need(regular!=null&&regular==0&&ot!=null&&ot==0,"Giờ chấm công OT chồng lấn với công hoặc OT khác.");
    }
    @Transactional("scheduleTransactionManager")
    public Item clock(AuthenticatedAccount principal,long id,boolean checkIn,ClockInput body){
        var actor=permissions.active(principal);
        if("ADMIN".equals(actor.roleCode())||actor.employeeId()==null)throw new ResponseStatusException(HttpStatus.FORBIDDEN,"Chỉ HR/nhân viên chấm công OT của chính mình.");
        permissions.requirePermission(actor,"OVERTIME_REQUEST_CREATE");
        db.query("SELECT employee_id FROM dbo.employees WITH(UPDLOCK,HOLDLOCK) WHERE employee_id=?",(rs,n)->rs.getLong(1),actor.employeeId());
        var ids=db.query("SELECT overtime_id FROM dbo.overtime_requests WITH(UPDLOCK,HOLDLOCK) WHERE overtime_id=? AND employee_id=?",(rs,n)->rs.getLong(1),id,actor.employeeId());
        if(ids.isEmpty())throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Không tìm thấy lịch OT của bạn.");
        var row=workspace.detail(principal,id).row();var o=row.ot();
        need(body!=null&&body.expectedVersion()!=null&&body.expectedVersion().equals(row.version()),"Dữ liệu đã thay đổi. Hãy tải lại lịch OT.");
        need("DA_DUYET".equals(o.status())&&o.confirmedBy()==null,"Chỉ chấm công OT đã duyệt và chưa xác nhận thực tế.");
        Long pending=db.queryForObject("SELECT COUNT_BIG(*) FROM dbo.overtime_request_details d JOIN dbo.employee_requests r ON r.request_id=d.request_id WHERE d.overtime_id=? AND r.status='CHO_DUYET'",Long.class,id);
        need(pending!=null&&pending==0&&db.queryForObject("SELECT COUNT_BIG(*) FROM dbo.ot_supplement_requests WHERE overtime_id=? AND status='CHO_XU_LY'",Long.class,id)==0,"Đơn đang có yêu cầu chờ xử lý.");
        var time=now();writable(o.requestedStart(),o.requestedEnd());
        String before=db.queryForObject("SELECT (SELECT * FROM dbo.overtime_requests WHERE overtime_id=? FOR JSON PATH,WITHOUT_ARRAY_WRAPPER)",String.class,id);
        if(checkIn){
            need(o.actualStart()==null&&o.actualEnd()==null,"Đã ghi nhận giờ vào/ra OT.");
            need(!time.isBefore(o.requestedStart().minusMinutes(30))&&time.isBefore(o.requestedEnd()),"Chỉ check-in từ 30 phút trước OT đến trước khi OT kết thúc.");
            writable(time,time.plusNanos(1));noOverlap(o.employeeId(),id,time,time.plusNanos(1));
            db.update("UPDATE dbo.overtime_requests SET actual_start=?,actual_break_minutes=0 WHERE overtime_id=?",time,id);
        }else{
            need(o.actualStart()!=null&&o.actualEnd()==null&&selfCheckIn(id),"Cần check-in OT trước; không check-out lại hoặc thay công nhập thủ công.");
            need(time.isAfter(o.actualStart()),"Giờ ra phải sau giờ vào.");
            writable(o.actualStart(),time);noOverlap(o.employeeId(),id,o.actualStart(),time);
            need(body.breaks()!=null,"Khai báo khoảng nghỉ thực tế.");
            need(!body.breaks().isEmpty()||body.noBreaksConfirmed(),"Xác nhận không nghỉ hoặc nhập các khoảng nghỉ thực tế; không tự lấy nghỉ dự kiến.");
            var rests=new ArrayList<>(body.breaks());
            if(rests.stream().anyMatch(b->b==null||b.breakStart()==null||b.breakEnd()==null))throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Thiếu ngày/giờ nghỉ thực tế.");
            rests.sort(Comparator.comparing(BreakInput::breakStart));LocalDateTime last=o.actualStart();long minutes=0;
            for(var b:rests){need(!b.breakStart().isBefore(last)&&b.breakEnd().isAfter(b.breakStart())&&!b.breakEnd().isAfter(time),"Khoảng nghỉ thực tế phải nằm trong giờ vào/ra và không chồng lấn.");minutes+=Duration.between(b.breakStart(),b.breakEnd()).toMinutes();last=b.breakEnd();}
            need(minutes<Duration.between(o.actualStart(),time).toMinutes(),"Thời gian làm thực tế phải lớn hơn thời gian nghỉ.");
            db.update("UPDATE dbo.overtime_breaks SET is_active=0 WHERE overtime_id=? AND is_active=1",id);
            for(var rest:rests)db.update("INSERT dbo.overtime_breaks(overtime_id,break_start,break_end,created_by) VALUES(?,?,?,?)",id,rest.breakStart(),rest.breakEnd(),actor.accountId());
            db.update("UPDATE dbo.overtime_requests SET actual_end=?,actual_break_minutes=? WHERE overtime_id=?",time,(int)minutes,id);
        }
        String after=db.queryForObject("SELECT (SELECT * FROM dbo.overtime_requests WHERE overtime_id=? FOR JSON PATH,WITHOUT_ARRAY_WRAPPER)",String.class,id);
        db.update("INSERT dbo.audit_logs(actor_account_id,action,target_table,target_record_key,reason,before_data_json,after_data_json) VALUES(?,?,'overtime_requests',?,?,?,?)",actor.accountId(),checkIn?"OT_SELF_CHECK_IN":"OT_SELF_CHECK_OUT",Long.toString(id),"Tự chấm công OT; giờ máy chủ",before,after);
        return new Item(workspace.detail(principal,id).row(),false,checkIn);
    }
}
