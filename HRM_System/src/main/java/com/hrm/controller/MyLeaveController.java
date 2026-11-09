package com.hrm.controller;
import java.security.Principal;import java.util.Map;import com.hrm.service.*;import org.springframework.web.bind.annotation.*;
@RestController @RequestMapping("/api/me/leave-options") public class MyLeaveController {
 private final PersonnelAccessService access;private final MaternityLeavePolicy policy;
 public MyLeaveController(PersonnelAccessService access,MaternityLeavePolicy policy){this.access=access;this.policy=policy;}
 @GetMapping public Map<String,Object> options(Principal principal){return policy.options(access.actor(principal).employeeId());}
}
