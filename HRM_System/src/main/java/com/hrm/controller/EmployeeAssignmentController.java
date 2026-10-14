
package com.hrm.controller;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
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

    public EmployeeAssignmentController(
            EmployeeAssignmentService assignmentService) {
        this.assignmentService = assignmentService;
    }

    @GetMapping("/employee-assignments")
    public List<EmployeeAssignmentDTO> getAllAssignments() {
        return assignmentService.getAllAssignments();
    }


    @PutMapping("/employees/{id}/transfer")
    public EmployeeAssignmentDTO transferEmployee(
            @PathVariable Long id,
            @Valid @RequestBody EmployeeTransferRequestDTO request) {

        return assignmentService.transferEmployee(id, request);
    }


    @GetMapping("/employees/{id}/assignments")
    public List<EmployeeAssignmentDTO> getEmployeeHistory(
            @PathVariable Long id) {
        return assignmentService.getEmployeeHistory(id);
    }
}
