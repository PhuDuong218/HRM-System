package com.hrm.controller;

import java.security.Principal;
import java.time.LocalDate;
import java.util.List;
import com.hrm.dto.*;
import com.hrm.service.*;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/leave-entitlements")
public class LeaveEntitlementController {
    private final LeaveEntitlementService service;
    private final PersonnelAccessService access;
    private final PersonnelCatalogService writes;
    public LeaveEntitlementController(LeaveEntitlementService service,PersonnelAccessService access,PersonnelCatalogService writes){this.service=service;this.access=access;this.writes=writes;}
    @GetMapping public Page<LeaveEntitlementDTO> getAllEntitlements(@RequestParam(required=false) Long employeeId,@RequestParam(required=false) Long leaveTypeId,
        @RequestParam(required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate fromDate,
        @RequestParam(required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate toDate,
        @RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="10") int size,Principal p){access.manager(p);return service.getAllEntitlements(employeeId,leaveTypeId,fromDate,toDate,page,size);}
    @GetMapping("/employee/{employeeId}") public List<LeaveEntitlementDTO> getByEmployeeId(@PathVariable Long employeeId,Principal p){access.ownerOrManager(p,employeeId);return service.getByEmployeeId(employeeId);}
    @GetMapping("/{id}") public LeaveEntitlementDTO getById(@PathVariable Long id,Principal p){access.actor(p);var result=service.getById(id);access.ownerOrManager(p,result.employeeId());return result;}
    @PostMapping @ResponseStatus(HttpStatus.CREATED)
    public LeaveEntitlementDTO addLeaveEntitlement(@Valid @RequestBody LeaveEntitlementRequestDTO request,Principal p){return writes.write(p,"leave_entitlements",null,"LEAVE_ENTITLEMENT_CREATE",()->service.addLeaveEntitlement(request),LeaveEntitlementDTO::entitlementId);}
    @PutMapping("/{id}") public LeaveEntitlementDTO updateLeaveEntitlement(@PathVariable Long id,@Valid @RequestBody LeaveEntitlementRequestDTO request,Principal p){return writes.write(p,"leave_entitlements",id,"LEAVE_ENTITLEMENT_UPDATE",()->service.updateLeaveEntitlement(id,request),LeaveEntitlementDTO::entitlementId);}
}
