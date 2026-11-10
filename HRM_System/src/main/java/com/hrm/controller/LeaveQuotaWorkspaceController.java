package com.hrm.controller;
import java.security.Principal;import java.util.Map;
import com.hrm.service.LeaveQuotaWorkspaceService;
import org.springframework.web.bind.annotation.*;
@RestController @RequestMapping("/api/leave-workspace")
public class LeaveQuotaWorkspaceController {
 private final LeaveQuotaWorkspaceService service;public LeaveQuotaWorkspaceController(LeaveQuotaWorkspaceService service){this.service=service;}
 @GetMapping public Map<String,Object> search(Principal p,@RequestParam int year,@RequestParam(required=false) Long departmentId,@RequestParam(required=false) Long employeeId,@RequestParam(required=false) Long leaveTypeId,@RequestParam(required=false) String q,@RequestParam(defaultValue="0") int page){return service.search(p,year,departmentId,employeeId,leaveTypeId,q,page);}
 @GetMapping("/options") public Map<String,Object> options(Principal p){return service.options(p);}
 @GetMapping("/{id}") public Map<String,Object> detail(Principal p,@PathVariable long id){return service.detail(p,id);}
 @PostMapping("/{id}/adjust") public Map<String,Object> adjust(Principal p,@PathVariable long id,@RequestBody LeaveQuotaWorkspaceService.Adjust input){return service.adjust(p,id,input);}
}
