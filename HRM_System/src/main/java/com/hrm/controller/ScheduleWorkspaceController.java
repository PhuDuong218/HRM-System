package com.hrm.controller;

import com.hrm.dto.PageResponse;
import com.hrm.dto.ScheduleChangeDTOs.Proposal;
import com.hrm.dto.ScheduleChangeDTOs.BreakWindow;
import com.hrm.dto.WorkTimeDTOs.*;
import com.hrm.security.AuthenticatedAccount;
import com.hrm.service.*;
import java.time.*;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

@RestController
@RequestMapping("/api/management/schedule-workspace")
public class ScheduleWorkspaceController {
    private final ScheduleWorkspaceService workspace;
    private final ScheduleChangeRequestService changes;
    private final WorkflowRequestQueryService requests;
    private final JdbcTemplate db;
    public ScheduleWorkspaceController(ScheduleWorkspaceService workspace, ScheduleChangeRequestService changes,
            WorkflowRequestQueryService requests, JdbcTemplate db) {
        this.workspace=workspace;this.changes=changes;this.requests=requests;this.db=db;
    }
    @GetMapping("/context")
    public ScheduleWorkspaceService.Context context(@AuthenticationPrincipal AuthenticatedAccount actor) {return workspace.context(actor);}
    @GetMapping("/employees")
    public List<ScheduleWorkspaceService.EmployeeOption> employees(@AuthenticationPrincipal AuthenticatedAccount actor,
            @RequestParam(defaultValue="") String q,@RequestParam(required=false) LocalDate date) {return workspace.employees(actor,q,date);}
    @GetMapping("/shifts")
    public List<ScheduleWorkspaceService.ShiftOption> shifts(@AuthenticationPrincipal AuthenticatedAccount actor) {return workspace.shifts(actor);}
    @GetMapping("/search")
    public PageResponse<ScheduleWorkspaceService.Row> search(@AuthenticationPrincipal AuthenticatedAccount actor,
            @RequestParam(required=false) Long employeeId,@RequestParam(required=false) Long departmentId,
            @RequestParam(required=false) Long shiftId,@RequestParam LocalDate from,@RequestParam LocalDate to,
            @RequestParam(defaultValue="0") int page) {return workspace.search(actor,employeeId,departmentId,shiftId,from,to,page);}
    @GetMapping("/schedules/{id}/history")
    public List<ScheduleWorkspaceService.History> history(@AuthenticationPrincipal AuthenticatedAccount actor,@PathVariable long id) {return workspace.history(actor,id);}
    @GetMapping("/my-search")
    public PageResponse<ScheduleWorkspaceService.Row> mine(@AuthenticationPrincipal AuthenticatedAccount actor,@RequestParam(required=false) Long shiftId,@RequestParam LocalDate from,@RequestParam LocalDate to,@RequestParam(defaultValue="0") int page){return workspace.mine(actor,shiftId,from,to,page);}
    @PostMapping("/schedules")
    public ScheduleView create(@AuthenticationPrincipal AuthenticatedAccount actor,@RequestBody ScheduleWorkspaceService.SaveInput body) {return workspace.save(actor,null,body);}
    @PutMapping("/schedules/{id}")
    public ScheduleView update(@AuthenticationPrincipal AuthenticatedAccount actor,@PathVariable long id,@RequestBody ScheduleWorkspaceService.SaveInput body) {return workspace.save(actor,id,body);}
    @GetMapping("/requests")
    public PageResponse<WorkflowRequestQueryService.RequestSummary> list(@AuthenticationPrincipal AuthenticatedAccount actor,
            @RequestParam(required=false) String status,@RequestParam(defaultValue="0") int page) {
        workspace.context(actor);return requests.list(actor,"DOI_LICH",status,null,page,10);
    }
    public record ChangeDetail(WorkflowRequestQueryService.RequestSummary request,long scheduleId,
            LocalDateTime originalStart,LocalDateTime originalEnd,Long proposedShiftId,
            LocalDateTime proposedStart,LocalDateTime proposedEnd,String employeeLabel,String shiftLabel,List<BreakWindow> breaks) {}
    @GetMapping("/requests/{id}")
    public ChangeDetail detail(@AuthenticationPrincipal AuthenticatedAccount actor,@PathVariable long id) {
        workspace.context(actor);
        var request=requests.get(actor,"DOI_LICH",id);
        var windows=db.query("SELECT break_start,break_end FROM dbo.schedule_change_request_breaks WHERE request_id=? ORDER BY break_start",
                (rs,n)->new BreakWindow(rs.getObject(1,LocalDateTime.class),rs.getObject(2,LocalDateTime.class)),id);
        return db.query("""
                SELECT d.schedule_id,d.original_start,d.original_end,d.proposed_shift_id,d.proposed_start,d.proposed_end,
                       CONCAT(e.employee_code,N' - ',e.full_name),CONCAT(s.shift_code,N' - ',s.shift_name)
                FROM dbo.schedule_change_request_details d JOIN dbo.employee_requests r ON r.request_id=d.request_id
                JOIN dbo.employees e ON e.employee_id=r.employee_id LEFT JOIN dbo.work_shifts s ON s.shift_id=d.proposed_shift_id
                WHERE d.request_id=?
                """,(rs,n)->new ChangeDetail(request,rs.getLong(1),rs.getObject(2,LocalDateTime.class),rs.getObject(3,LocalDateTime.class),
                        rs.getObject(4,Long.class),rs.getObject(5,LocalDateTime.class),rs.getObject(6,LocalDateTime.class),rs.getString(7),rs.getString(8),windows),id)
                .stream().findFirst().orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"Không tìm thấy yêu cầu đổi lịch"));
    }
    public record Decision(boolean approve,String rejectionReason) {}
    @PostMapping("/requests/{id}/decision")
    public void decide(@AuthenticationPrincipal AuthenticatedAccount actor,@PathVariable long id,@RequestBody Decision body) {
        workspace.context(actor);changes.decide(actor,id,body.approve(),body.rejectionReason());
    }
    public record Created(long requestId) {}
    @PostMapping("/requests")
    public Created submit(@AuthenticationPrincipal AuthenticatedAccount actor,@RequestBody Proposal body) {
        workspace.context(actor);return new Created(changes.submit(actor,body));
    }
}
