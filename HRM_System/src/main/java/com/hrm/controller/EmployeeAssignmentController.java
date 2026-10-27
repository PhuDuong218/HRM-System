
package com.hrm.controller;

import java.util.List;
import java.security.Principal;

import org.springframework.data.domain.Page;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import jakarta.validation.Valid;

import com.hrm.dto.EmployeeTransferRequestDTO;
import com.hrm.dto.EmployeeAssignmentDTO;
import com.hrm.service.EmployeeAssignmentService;

@RestController
@RequestMapping
public class EmployeeAssignmentController {

    private final EmployeeAssignmentService assignmentService;
    private final com.hrm.service.DepartmentHeadAccessService access;
    private final com.hrm.service.EmployeeTransferAuditService transfers;

    public EmployeeAssignmentController(
            EmployeeAssignmentService assignmentService,
            com.hrm.service.DepartmentHeadAccessService access,
            com.hrm.service.EmployeeTransferAuditService transfers) {
        this.assignmentService = assignmentService;
        this.access=access; this.transfers=transfers;
    }

    @GetMapping("/employee-assignments")
public Page<EmployeeAssignmentDTO> getAllAssignments(
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "10") int size, Principal principal) {

    access.requireManager(principal);
    return assignmentService.getAssignmentsPage(page, size);
}

@GetMapping("/employees/{id}/assignments")
public Page<EmployeeAssignmentDTO> getEmployeeHistory(
        @PathVariable Long id,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "10") int size, Principal principal) {

    access.requireManager(principal);
    return assignmentService.getHistoryPage(id, page, size);
}


    @PutMapping("/employees/{id}/transfer")
    public EmployeeAssignmentDTO transferEmployee(
            @PathVariable Long id,
            @Valid @RequestBody EmployeeTransferRequestDTO request, Principal principal) {

        return transfers.transfer(id, request, access.requireManager(principal));
    }

}
