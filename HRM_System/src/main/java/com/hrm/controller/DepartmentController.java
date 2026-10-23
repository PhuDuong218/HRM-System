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

import com.hrm.dto.DepartmentDTO;
import com.hrm.dto.DepartmentRequestDTO;
import com.hrm.service.DepartmentService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/departments")
public class DepartmentController {

    public final DepartmentService departmentService;

    public DepartmentController(DepartmentService departmentService) {
        this.departmentService = departmentService;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('department.view')")
    public List<DepartmentDTO> getAllDepartments() {
        return departmentService.getAllDepartment();
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('department.view')")
    public DepartmentDTO getDepartment(@PathVariable Long id) {
        return departmentService.getDepartment(id);
    }

    @PostMapping
    @PreAuthorize("hasAuthority('department.create')")
    public DepartmentDTO addDepartment(@Valid @RequestBody DepartmentRequestDTO requestDTO) {
        return departmentService.addDepartment(requestDTO);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('department.update')")
    public DepartmentDTO updateDepartment(@PathVariable Long id, @Valid @RequestBody DepartmentRequestDTO requestDTO) {
        return departmentService.updateDepartment(id, requestDTO);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('department.delete')")
    public String deleteDepartment(@PathVariable Long id) {
        departmentService.deleteDepartment(id);
        return "Đã xóa phòng ban có mã: " + id;
    }
}
