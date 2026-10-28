package com.hrm.controller;

import java.security.Principal;
import java.util.*;
import com.hrm.dto.EmployeeRequestDTOs.*;
import com.hrm.service.*;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/employee-requests")
public class EmployeeRequestController {
    private final EmployeeRequestService service;
    private final PersonnelAccessService access;
    private final ResignationExecutionService runner;
    public EmployeeRequestController(EmployeeRequestService service,PersonnelAccessService access,ResignationExecutionService runner) {this.service=service;this.access=access;this.runner=runner;}
    @PostMapping("/leave") @ResponseStatus(HttpStatus.CREATED)
    public Map<String,Object> leave(@Valid @RequestBody Leave body,Principal p){return service.leave(body,p);}
    @PostMapping("/resignation") @ResponseStatus(HttpStatus.CREATED)
    public Map<String,Object> resignation(@Valid @RequestBody Resignation body,Principal p){return service.resign(body,p);}
    @GetMapping("/mine")
    public Page<Map<String,Object>> mine(@RequestParam(required=false) String status,@RequestParam(required=false) String type,
            @RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="10") int size,Principal p){return service.list(true,status,type,page,size,p);}
    @GetMapping
    public Page<Map<String,Object>> list(@RequestParam(required=false) String status,@RequestParam(required=false) String type,
            @RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="10") int size,Principal p){return service.list(false,status,type,page,size,p);}
    @GetMapping("/{id}") public Map<String,Object> detail(@PathVariable long id,Principal p){return service.detail(id,p);}
    @PostMapping("/{id}/approve") public Map<String,Object> approve(@PathVariable long id,@Valid @RequestBody Decision body,Principal p){return service.approve(id,body,p);}
    @PostMapping("/{id}/reject") public Map<String,Object> reject(@PathVariable long id,@Valid @RequestBody Rejection body,Principal p){return service.reject(id,body,p);}
    @PostMapping("/{id}/withdraw") public Map<String,Object> withdraw(@PathVariable long id,@Valid @RequestBody Rejection body,Principal p){return service.withdraw(id,body,p);}
    @GetMapping("/balances/{employeeId}") public List<Map<String,Object>> balances(@PathVariable long employeeId,Principal p){return service.balances(employeeId,p);}
    @PutMapping("/leave-policies/{leaveTypeId}") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void policy(@PathVariable long leaveTypeId,@Valid @RequestBody Policy body,Principal p){service.policy(leaveTypeId,body,p);}
    @GetMapping("/leave-policies/{leaveTypeId}") public Map<String,Object> policy(@PathVariable long leaveTypeId,Principal p){return service.policy(leaveTypeId,p);}
    @PostMapping("/resignations/execute-due") public List<Map<String,Object>> execute(Principal p){return runner.run(access.admin(p).accountId());}
}
