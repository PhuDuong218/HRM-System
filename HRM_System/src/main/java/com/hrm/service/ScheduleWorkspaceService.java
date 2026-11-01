package com.hrm.service;

import com.hrm.dto.DetailedWorkTimeDTOs.ScheduleWithBreaks;
import com.hrm.dto.PageResponse;
import com.hrm.dto.WorkTimeDTOs.*;
import com.hrm.security.AuthenticatedAccount;
import java.time.*;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.json.JsonMapper;

/** Schedule-only integration; does not enable unrelated task APIs. */
@Service
public class ScheduleWorkspaceService {
    public record EmployeeOption(long employeeId, String employeeCode, String fullName) {}
    public record ShiftOption(ShiftView shift, boolean breaksVerified) {}
    public record Context(boolean canManage, boolean canSubmitOwn, Long employeeId) {}
    public record SaveInput(Long employeeId, Long shiftId, LocalDate workDate, String note) {}
    public record Row(ScheduleView schedule, String employeeLabel, String shiftLabel, String breakDetailStatus,String state,String cancellationReason) {}
    private record Label(long id, String employee, String shift, String status,String state,String cancellationReason) {}
    private final JdbcTemplate db;
    private final WorkShiftService shifts;
    private final WorkScheduleService schedules;
    private final ScheduleQueryService queries;
    private final ScheduleWritePermissionService permissions;
    private final DetailedWorkTimeService detailed;
    private final JsonMapper json;

    public ScheduleWorkspaceService(JdbcTemplate db, WorkShiftService shifts, WorkScheduleService schedules,
            ScheduleQueryService queries, ScheduleWritePermissionService permissions, DetailedWorkTimeService detailed,
            JsonMapper json) {
        this.db=db; this.shifts=shifts; this.schedules=schedules; this.queries=queries;
        this.permissions=permissions; this.detailed=detailed; this.json=json;
    }

    public Context context(AuthenticatedAccount actor) {
        if (actor == null || actor.accountId() == null || actor.roleCode() == null)
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Yêu cầu đăng nhập");
        var roles = db.query("""
                SELECT r.role_code,a.employee_id FROM dbo.accounts a
                JOIN dbo.roles r ON r.role_id=a.role_id WHERE a.account_id=? AND a.is_active=1
                """, (rs,n) -> new Object[]{rs.getString(1),rs.getObject(2,Long.class)}, actor.accountId());
        if (roles.size()!=1 || !Objects.equals(roles.get(0)[0],actor.roleCode())
                || !Objects.equals(roles.get(0)[1],actor.employeeId())
                || !Set.of("ADMIN","HR").contains(actor.roleCode()))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Không có quyền quản lý lịch");
        return new Context(true,"HR".equals(actor.roleCode()) && actor.employeeId()!=null,actor.employeeId());
    }

    public List<EmployeeOption> employees(AuthenticatedAccount actor, String query, LocalDate date) {
        context(actor);
        String q=query==null ? "" : query.trim();
        if (q.length()>200 || q.matches("-\\d+(\\.\\d+)?"))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Nhân viên không hợp lệ");
        LocalDate on=date==null ? LocalDate.now(ZoneId.of("Asia/Ho_Chi_Minh")) : date;
        String term="%"+q.replace("[","[[]").replace("%","[%]").replace("_","[_]")+"%";
        StringBuilder sql=new StringBuilder("""
                SELECT TOP (50) e.employee_id,e.employee_code,e.full_name FROM dbo.employees e
                WHERE e.employment_status='DANG_LAM' AND e.termination_date IS NULL
                  AND e.hire_date<=? AND CONCAT(e.employee_code,N' - ',e.full_name) COLLATE Vietnamese_100_CI_AI LIKE ?
                """);
        List<Object> args=new ArrayList<>(List.of(on,term));
        if ("HR".equals(actor.roleCode())) {
            sql.append("""
                    AND EXISTS(SELECT 1 FROM dbo.accounts a JOIN dbo.roles r ON r.role_id=a.role_id
                               WHERE a.employee_id=e.employee_id AND a.is_active=1 AND r.role_code='EMPLOYEE')
                    AND EXISTS(SELECT 1 FROM dbo.employee_assignments ea
                        JOIN dbo.hr_department_scopes hs ON hs.department_id=ea.department_id
                        WHERE ea.employee_id=e.employee_id AND ea.effective_from<=?
                          AND (ea.effective_to IS NULL OR ea.effective_to>?)
                          AND hs.hr_account_id=? AND hs.revoked_at IS NULL
                          AND hs.effective_from<=SYSUTCDATETIME()
                          AND (hs.effective_to IS NULL OR hs.effective_to>SYSUTCDATETIME()))
                    """);
            args.add(on);args.add(on);args.add(actor.accountId());
        }
        sql.append(" ORDER BY e.employee_code,e.employee_id");
        return db.query(sql.toString(),(rs,n)->new EmployeeOption(rs.getLong(1),rs.getString(2),rs.getString(3)),args.toArray());
    }

    public List<ShiftOption> shifts(AuthenticatedAccount actor) {
        context(actor);
        return shifts.list().stream().map(s -> {
            var breaks=detailed.shiftBreaks(s.shiftId());
            long total=breaks.stream().mapToLong(b->b.endMinute()-b.startMinute()).sum();
            return new ShiftOption(s,total==s.breakMinutes());
        }).toList();
    }

    public PageResponse<Row> search(AuthenticatedAccount actor, Long employee, Long department, Long shift,
            LocalDate from, LocalDate to, int page) {
        context(actor);
        var result=queries.search(actor,employee,department,shift,from,to,page,false);
        if (result.items().isEmpty()) return new PageResponse<>(List.of(),result.page(),result.size(),result.totalElements(),result.totalPages());
        var ids=result.items().stream().map(ScheduleView::scheduleId).toList();
        String markers=String.join(",",Collections.nCopies(ids.size(),"?"));
        var labels=db.query("""
                SELECT s.schedule_id,CONCAT(e.employee_code,N' - ',e.full_name),
                       CONCAT(w.shift_code,N' - ',w.shift_name),s.break_detail_status,
                       CASE WHEN s.cancelled_at IS NOT NULL OR latest_state.action='WORK_SCHEDULE_CANCELLED' THEN 'CANCELLED'
                            WHEN latest_state.action='WORK_SCHEDULE_EDIT_LOCKED' THEN 'EDIT_LOCKED' ELSE 'ACTIVE' END,s.cancellation_reason
                FROM dbo.work_schedules s JOIN dbo.employees e ON e.employee_id=s.employee_id
                LEFT JOIN dbo.work_shifts w ON w.shift_id=s.shift_id
                OUTER APPLY(SELECT TOP(1) a.action FROM dbo.audit_logs a
                    WHERE a.target_table='work_schedules' AND a.target_record_key=CONVERT(varchar(30),s.schedule_id)
                      AND a.action IN ('WORK_SCHEDULE_EDIT_LOCKED','WORK_SCHEDULE_EDIT_UNLOCKED','WORK_SCHEDULE_CANCELLED')
                    ORDER BY a.occurred_at DESC,a.audit_id DESC) latest_state
                WHERE s.schedule_id IN (
                """+markers+")",(rs,n)->new Label(rs.getLong(1),rs.getString(2),rs.getString(3),rs.getString(4),rs.getString(5),rs.getString(6)),ids.toArray());
        Map<Long,Label> byId=new HashMap<>(); labels.forEach(l->byId.put(l.id(),l));
        var rows=result.items().stream().map(s->{
            Label l=byId.get(s.scheduleId());
            return new Row(s,l==null ? Long.toString(s.employeeId()):l.employee(),l==null?"":l.shift(),l==null?"LEGACY_UNVERIFIED":l.status(),l==null?"UNKNOWN":l.state(),l==null?null:l.cancellationReason());
        }).toList();
        return new PageResponse<>(rows,result.page(),result.size(),result.totalElements(),result.totalPages());
    }

    private ScheduleInput input(SaveInput body) {
        if (body==null || body.employeeId()==null || body.employeeId()<=0 || body.shiftId()==null || body.shiftId()<=0
                || body.workDate()==null || (body.note()!=null && body.note().length()>1000))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Nhân viên, ca hoặc ngày làm không hợp lệ");
        db.query("SELECT shift_id FROM dbo.work_shifts WITH(HOLDLOCK) WHERE shift_id=?",(rs,n)->rs.getLong(1),body.shiftId());
        ShiftView shift=shifts.get(body.shiftId());
        if (!shift.active()) throw new ResponseStatusException(HttpStatus.CONFLICT,"Ca đã bị khóa");
        return new ScheduleInput(body.employeeId(),body.shiftId(),body.workDate().atTime(shift.startTime()),
                body.workDate().plusDays(shift.endDayOffset()).atTime(shift.endTime()),shift.breakMinutes(),body.note());
    }

    @Transactional("scheduleTransactionManager")
    public ScheduleView save(AuthenticatedAccount actor, Long id, SaveInput body) {
        context(actor);
        ScheduleInput input=input(body);
        if (id==null) permissions.requireCreate(actor,input.employeeId(),input.plannedStart());
        else {
            if (id<=0) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Lịch không hợp lệ");
            db.query("SELECT schedule_id FROM dbo.work_schedules WITH(UPDLOCK,HOLDLOCK) WHERE schedule_id=?",(rs,n)->rs.getLong(1),id);
            permissions.requireUpdate(actor,schedules.get(id),input);
            Long count=db.queryForObject("SELECT COUNT_BIG(*) FROM dbo.attendance_records WITH(HOLDLOCK) WHERE schedule_id=?",Long.class,id);
            if (count!=null && count>0) throw new ResponseStatusException(HttpStatus.CONFLICT,"Lịch đã có chấm công; cần dùng quy trình đổi lịch");
        }
        var request=new ScheduleWithBreaks(input,List.of());
        ScheduleView saved=id==null ? detailed.createSchedule(actor,request) : detailed.updateSchedule(actor,id,request);
        db.update("""
                INSERT INTO dbo.audit_logs(actor_account_id,action,target_table,target_record_key,reason,after_data_json)
                VALUES(?,?,'work_schedules',?,?,?)
                """,actor.accountId(),id==null?"WORK_SCHEDULE_CREATE":"WORK_SCHEDULE_UPDATE",Long.toString(saved.scheduleId()),
                "Lưu lịch theo mẫu ca và khoảng nghỉ chi tiết",json.writeValueAsString(saved));
        return saved;
    }
}
