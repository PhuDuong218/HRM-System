package com.hrm.controller;
import com.hrm.security.AuthenticatedAccount;
import com.hrm.service.AttendanceActualBreakService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
@RestController
@RequestMapping("/api/attendance-workspace")
public class AttendanceActualBreakController {
 private final AttendanceActualBreakService service;
 public AttendanceActualBreakController(AttendanceActualBreakService service){this.service=service;}
 @GetMapping("/schedules/{id}/actual-breaks")
 public Object get(@AuthenticationPrincipal AuthenticatedAccount actor,@PathVariable long id,@RequestParam(defaultValue="false") boolean mine){return service.detail(actor,mine,id);}
 @PostMapping("/records/{id}/actual-breaks")
 public Object save(@AuthenticationPrincipal AuthenticatedAccount actor,@PathVariable long id,@RequestBody AttendanceActualBreakService.Save body){return service.save(actor,id,body);}
}
