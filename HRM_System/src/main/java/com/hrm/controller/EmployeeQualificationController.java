package com.hrm.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.hrm.dto.EmployeeQualificationDTO;
import com.hrm.dto.EmployeeQualificationRequestDTO;
import com.hrm.service.EmployeeQualificationService;

import jakarta.validation.Valid;

@RestController
@RequestMapping
public class EmployeeQualificationController {

    private final EmployeeQualificationService service;

    public EmployeeQualificationController(EmployeeQualificationService service) {
        this.service = service;
    }

    @GetMapping("/employees/{employeeId}/qualifications")
    public List<EmployeeQualificationDTO> getEmployeeQualificationsByEmployeeId(@PathVariable Long employeeId) {
        return service.getEmployeeQualificationsByEmployeeId(employeeId);
    }

    @GetMapping("/employee-qualifications/{id}")
    public EmployeeQualificationDTO getEmployeeQualificationById(@PathVariable Long id) {
        return service.getEmployeeQualificationById(id);
    }

    @PostMapping("/employees/{employeeId}/qualifications")
    @ResponseStatus(HttpStatus.CREATED)
    public EmployeeQualificationDTO addEmployeeQualification(@PathVariable Long employeeId,
            @Valid @RequestBody EmployeeQualificationRequestDTO request) {
        return service.addEmployeeQualification(employeeId, request);
    }

    @PutMapping("/employee-qualifications/{id}")
    public EmployeeQualificationDTO updateEmployeeQualification(@PathVariable Long id,
            @Valid @RequestBody EmployeeQualificationRequestDTO request) {
        return service.updateEmployeeQualification(id, request);
    }

    // Xoa mem: khong xoa du lieu khoi SQL Server.
    @DeleteMapping("/employee-qualifications/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteEmployeeQualification(@PathVariable Long id) {
        service.deleteEmployeeQualification(id);
    }

    @PutMapping("/employee-qualifications/{id}/primary")
    public EmployeeQualificationDTO setPrimaryEmployeeQualification(@PathVariable Long id) {
        return service.setPrimaryEmployeeQualification(id);
    }
}
