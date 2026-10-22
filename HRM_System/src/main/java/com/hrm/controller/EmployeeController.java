package com.hrm.controller;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.hrm.dto.EmployeeDTO;
import com.hrm.dto.EmployeeRequestDTO;
import com.hrm.enums.EmploymentStatus;
import com.hrm.service.EmployeeService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/employees")
public class EmployeeController {

    private final EmployeeService employeeService;

    public EmployeeController(EmployeeService employeeService) {
        this.employeeService = employeeService;
    }

    @GetMapping
    public List<EmployeeDTO> getAllEmployees() {
        return employeeService.getAllEmployees();
    }

    @GetMapping("/{id}")
    public EmployeeDTO getEmployee(@PathVariable Long id) {
        return employeeService.getEmployeeById(id);
    }

    @PostMapping
    public EmployeeDTO addEmployee(@Valid @RequestBody EmployeeRequestDTO requestDTO) {
        return employeeService.addEmployee(requestDTO);
    }

    @PutMapping("/{id}")
    public EmployeeDTO updateEmployee(@PathVariable Long id, @Valid @RequestBody EmployeeRequestDTO requestDTO) {
        // Tầng Service phải tự gọi convertToDTO bên trong nó
        return employeeService.updateEmployee(id, requestDTO);
    }

    @DeleteMapping("/{id}")
    public String deleteEmployee(@PathVariable Long id) {
        employeeService.deleteEmployee(id);
        return "Đã xóa nhân viên có mã: " + id;
    }

    @GetMapping("/search")
public Page<EmployeeDTO> searchEmployees(
        @RequestParam(required = false) String keyword,
        @RequestParam(required = false) EmploymentStatus status,
        @RequestParam(required = false) Long departmentId,
        @RequestParam(required = false) Long positionId,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "10") int size) {

    return employeeService.searchEmployees(
            keyword,
            status,
            departmentId,
            positionId,
            page,
            size
    );
}
}
