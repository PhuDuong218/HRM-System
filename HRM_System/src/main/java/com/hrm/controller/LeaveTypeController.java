package com.hrm.controller;

import java.security.Principal;

import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.hrm.dto.LeaveTypeDTO;
import com.hrm.dto.LeaveTypeRequestDTO;
import com.hrm.service.PersonnelAccessService;
import com.hrm.service.PersonnelCatalogService;
import com.hrm.service.LeaveTypeService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/leave-types")
public class LeaveTypeController {
    private final LeaveTypeService service;
    private final PersonnelAccessService access;
    private final PersonnelCatalogService writes;

    public LeaveTypeController(LeaveTypeService service, PersonnelAccessService access, PersonnelCatalogService writes) {
        this.service = service;
        this.access = access;
        this.writes = writes;
    }

    @GetMapping
    public Page<LeaveTypeDTO> getAllLeaveTypes(@RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size, Principal p) {
        access.actor(p);
        return service.getLeaveTypesPage(page, size);
    }

    @GetMapping("/{id}")
    public LeaveTypeDTO getLeaveTypeById(@PathVariable Long id, Principal p) {
        access.actor(p);
        return service.getLeaveTypeById(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public LeaveTypeDTO addLeaveType(@Valid @RequestBody LeaveTypeRequestDTO request, Principal p) {
        return writes.write(p, "leave_types", null, "LEAVE_TYPE_CREATE", () -> service.addLeaveType(request),
                LeaveTypeDTO::leaveTypeId);
    }

    @PutMapping("/{id}")
    public LeaveTypeDTO updateLeaveType(@PathVariable Long id, @Valid @RequestBody LeaveTypeRequestDTO request,
            Principal p) {
        return writes.write(p, "leave_types", id, "LEAVE_TYPE_UPDATE", () -> service.updateLeaveType(id, request),
                LeaveTypeDTO::leaveTypeId);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteLeaveType(@PathVariable Long id, Principal p) {
        writes.write(p, "leave_types", id, "LEAVE_TYPE_DEACTIVATE", () -> {
            service.deleteLeaveType(id);
            return id;
        }, Long::longValue);
    }
}
