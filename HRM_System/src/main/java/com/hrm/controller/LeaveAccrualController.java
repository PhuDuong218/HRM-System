package com.hrm.controller;
import java.security.Principal;
import java.util.*;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
import com.hrm.service.LeaveAccrualService;
@RestController @RequestMapping("/employee-requests/leave-accrual")
public class LeaveAccrualController {
 private final LeaveAccrualService service;public LeaveAccrualController(LeaveAccrualService service){this.service=service;}
 @GetMapping("/{type}") public Map<String,Object> get(@PathVariable long type,Principal p){return service.get(type,p);}
 @PutMapping("/{type}") public LeaveAccrualService.Policy save(@PathVariable long type,@Valid @RequestBody LeaveAccrualService.Policy body,Principal p){return service.save(type,body,p);}
 @PostMapping("/{type}/generate") public List<Map<String,Object>> generate(@PathVariable long type,@Valid @RequestBody LeaveAccrualService.Run body,Principal p){return service.generate(type,body,p);}
}
