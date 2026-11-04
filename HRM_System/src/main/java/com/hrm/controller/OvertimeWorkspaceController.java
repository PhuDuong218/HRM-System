package com.hrm.controller;

import com.hrm.dto.OvertimeDTOs.*;
import com.hrm.security.AuthenticatedAccount;
import com.hrm.service.OvertimeWorkspaceService;
import com.hrm.service.OvertimeRequestWorkflowService;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/** Explicit OT workspace API, independent of the legacy all-task switch. */
@RestController
@RequestMapping("/api/overtime/workspace")
public class OvertimeWorkspaceController {
    private final OvertimeWorkspaceService service;
    public OvertimeWorkspaceController(OvertimeWorkspaceService service){this.service=service;}
    @GetMapping("/context") public Object context(@AuthenticationPrincipal AuthenticatedAccount a){return service.context(a);}
    @GetMapping("/departments") public Object departments(@AuthenticationPrincipal AuthenticatedAccount a){return service.departments(a);}
    @GetMapping({"/requests","/employees"}) public Object search(@AuthenticationPrincipal AuthenticatedAccount a,
        jakarta.servlet.http.HttpServletRequest servlet,@RequestParam(defaultValue="false") boolean mine,@RequestParam LocalDate from,@RequestParam LocalDate to,
        @RequestParam(required=false) String q,@RequestParam(required=false) Long departmentId,@RequestParam(required=false) String status,
        @RequestParam(required=false) String actual,@RequestParam(required=false) String registration,@RequestParam(required=false) Long employeeId,@RequestParam(defaultValue="0") int page){
        var f=new OvertimeWorkspaceService.Filter(mine,from,to,q,departmentId,status,actual,registration,employeeId,page);
        return servlet.getRequestURI().endsWith("/employees")?service.employees(a,f):service.search(a,f);
    }
    @GetMapping("/requests/{id}") public Object detail(@AuthenticationPrincipal AuthenticatedAccount a,@PathVariable long id){return service.detail(a,id);}
    @PostMapping("/requests") public Object register(@AuthenticationPrincipal AuthenticatedAccount a,@Valid @RequestBody Register body){return service.register(a,body);}
    public record Edit(String expectedVersion,@Valid Register request){}
    @PutMapping("/requests/{id}") public Object edit(@AuthenticationPrincipal AuthenticatedAccount a,@PathVariable long id,@RequestBody Edit body){return service.edit(a,id,body.expectedVersion(),body.request());}
    @PostMapping("/requests/{id}/{action:approve|reject|cancel|actual|amend-actual|confirm|reopen}") public Object action(@AuthenticationPrincipal AuthenticatedAccount a,@PathVariable long id,@PathVariable String action,@RequestBody OvertimeWorkspaceService.Mutation body){return service.action(a,id,action,body);}
    public record WorkflowSubmission(String expectedVersion,String reason,List<OvertimeRequestWorkflowService.Window> windows){}
    @PostMapping("/requests/{id}/cancel-request") public Object cancellation(@AuthenticationPrincipal AuthenticatedAccount a,@PathVariable long id,@RequestBody WorkflowSubmission body){return Map.of("requestId",service.requestCancellation(a,id,body.expectedVersion(),body.reason()));}
    @PostMapping("/requests/{id}/overflow-request") public Object overflow(@AuthenticationPrincipal AuthenticatedAccount a,@PathVariable long id,@RequestBody WorkflowSubmission body){return Map.of("requestId",service.requestOverflow(a,id,body.expectedVersion(),body.reason(),body.windows()));}
    @GetMapping("/inbox") public Object inbox(@AuthenticationPrincipal AuthenticatedAccount a,@RequestParam(defaultValue="false") boolean mine,
        @RequestParam LocalDate from,@RequestParam LocalDate to,@RequestParam(required=false) String type,@RequestParam(required=false) String status,@RequestParam(defaultValue="0") int page){return service.inbox(a,mine,from,to,type,status,page);}
    public record Decision(boolean approve,String reason,List<OvertimeRequestWorkflowService.DecisionWindow> accepted){}
    @PostMapping("/inbox/{id}/decision") public void decide(@AuthenticationPrincipal AuthenticatedAccount a,@PathVariable long id,@RequestBody Decision body){service.decideRequest(a,id,body.approve(),body.reason(),body.accepted());}
}
