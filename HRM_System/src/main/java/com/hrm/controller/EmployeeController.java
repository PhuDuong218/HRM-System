package com.hrm.controller;

import java.util.List;

import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;

import com.hrm.dto.EmployeeDTO;
import com.hrm.dto.EmployeeRequestDTO;
import com.hrm.model.Employee;
import com.hrm.service.EmployeeService;
import com.hrm.security.AuthenticatedAccount;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/employees")
public class EmployeeController {

    private final EmployeeService employeeService;

    public EmployeeController(EmployeeService employeeService) {
        this.employeeService = employeeService;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('employee.view')")
    public List<EmployeeDTO> getAllEmployees(Authentication authentication) {
        return employeeService.getAllEmployees((AuthenticatedAccount) authentication.getPrincipal());
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('employee.view')")
    public EmployeeDTO getEmployee(@PathVariable Long id, Authentication authentication) {
        return employeeService.getEmployeeById(id, (AuthenticatedAccount) authentication.getPrincipal());
    }

    @PostMapping
    @PreAuthorize("hasAuthority('employee.create')")
    public EmployeeDTO addEmployee(@Valid @RequestBody EmployeeRequestDTO requestDTO) {
        return employeeService.addEmployee(requestDTO);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('employee.update')")
    public EmployeeDTO updateEmployee(@PathVariable Long id, @Valid @RequestBody EmployeeRequestDTO requestDTO) {
        // Tầng Service phải tự gọi convertToDTO bên trong nó
        return employeeService.updateEmployee(id, requestDTO);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('employee.delete')")
    public String deleteEmployee(@PathVariable Long id) {
        employeeService.deleteEmployee(id);
        return "Đã xóa nhân viên có mã: " + id;
    }
}
