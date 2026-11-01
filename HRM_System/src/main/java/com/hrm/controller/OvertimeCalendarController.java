package com.hrm.controller;
import com.hrm.security.AuthenticatedAccount;
import com.hrm.service.OvertimeCalendarService;
import java.time.LocalDate;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
@RestController
@RequestMapping("/api/overtime/workspace/calendar")
public class OvertimeCalendarController {
 private final OvertimeCalendarService service;
 public OvertimeCalendarController(OvertimeCalendarService service){this.service=service;}
 @GetMapping public Object calendar(@AuthenticationPrincipal AuthenticatedAccount actor,@RequestParam(defaultValue="true") boolean mine,@RequestParam LocalDate from,@RequestParam LocalDate to,@RequestParam(required=false) Long employeeId,@RequestParam(required=false) Long departmentId,@RequestParam(defaultValue="0") int page){return service.calendar(actor,mine,from,to,employeeId,departmentId,page);}
 @PostMapping("/{id}/{action:check-in|check-out}") public Object clock(@AuthenticationPrincipal AuthenticatedAccount actor,@PathVariable long id,@PathVariable String action,@RequestBody OvertimeCalendarService.ClockInput body){return service.clock(actor,id,action.equals("check-in"),body);}
}
