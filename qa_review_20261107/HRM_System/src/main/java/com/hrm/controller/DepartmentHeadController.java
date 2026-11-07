package com.hrm.controller;

import java.security.Principal;
import java.time.LocalDate;
import com.hrm.dto.*;
import com.hrm.service.DepartmentHeadService;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/departments/{departmentId}/head")
public class DepartmentHeadController {
    private final DepartmentHeadService service;
    public DepartmentHeadController(DepartmentHeadService service) { this.service=service; }

    @GetMapping
    public ResponseEntity<DepartmentHeadDTO> get(@PathVariable Long departmentId,
            @RequestParam(required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate asOf, Principal principal) {
        var head=service.getHead(departmentId,asOf,principal);
        return head==null?ResponseEntity.noContent().build():ResponseEntity.ok(head);
    }
    @GetMapping("/history")
    public PageResponse<DepartmentHeadDTO> history(@PathVariable Long departmentId,
            @RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="10") int size,Principal principal) {
        return service.history(departmentId,page,size,principal);
    }
    @GetMapping("/candidates")
    public PageResponse<EmployeeAssignmentDTO> candidates(@PathVariable Long departmentId,
            @RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="10") int size,Principal principal) {
        return service.candidates(departmentId,page,size,principal);
    }
    @PutMapping
    public DepartmentHeadDTO appoint(@PathVariable Long departmentId,@Valid @RequestBody DepartmentHeadRequest request,Principal principal) {
        return service.appoint(departmentId,request,principal);
    }
    @PostMapping("/end")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void end(@PathVariable Long departmentId,@Valid @RequestBody DepartmentHeadEndRequest request,Principal principal) {
        service.end(departmentId,request,principal);
    }
}
