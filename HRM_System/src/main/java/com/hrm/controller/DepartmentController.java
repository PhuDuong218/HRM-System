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

import com.hrm.dto.DepartmentDTO;
import com.hrm.dto.DepartmentRequestDTO;
import com.hrm.service.DepartmentService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/departments")
public class DepartmentController {

    public final DepartmentService departmentService;
    private final com.hrm.service.DepartmentHeadAccessService access;
    private final com.hrm.service.CatalogAuditService catalog;

    public DepartmentController(DepartmentService departmentService, com.hrm.service.DepartmentHeadAccessService access,
            com.hrm.service.CatalogAuditService catalog) {
        this.departmentService = departmentService;
        this.access=access;
        this.catalog=catalog;
    }

    @GetMapping
    public Page<DepartmentDTO> getAllDepartments(
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "10") int size,
        @RequestParam(required = false) String keyword,
        @RequestParam(required = false) Boolean active, java.security.Principal principal) {

    access.requireManager(principal);

    return departmentService.searchDepartments(keyword, active, page, size);
}

    @GetMapping("/{id}")
    public DepartmentDTO getDepartment(@PathVariable Long id, java.security.Principal principal) {
        access.requireManager(principal);
        return departmentService.getDepartment(id);
    }

    @PostMapping
    @org.springframework.web.bind.annotation.ResponseStatus(org.springframework.http.HttpStatus.CREATED)
    public DepartmentDTO addDepartment(@Valid @RequestBody DepartmentRequestDTO requestDTO, java.security.Principal principal) {
        Long actor=access.requireManager(principal);
        return catalog.createDepartment(requestDTO, actor);
    }

    @PutMapping("/{id}")
    public DepartmentDTO updateDepartment(@PathVariable Long id, @Valid @RequestBody DepartmentRequestDTO requestDTO, java.security.Principal principal) {
        Long actor=access.requireManager(principal);
        return catalog.updateDepartment(id, requestDTO, actor);
    }

    @DeleteMapping(value="/{id}", produces="text/plain;charset=UTF-8")
    public String deleteDepartment(@PathVariable Long id, java.security.Principal principal) {
        Long actor=access.requireManager(principal);
        catalog.deactivateDepartment(id, actor);
        return "Đã ngừng sử dụng phòng ban có mã: " + id;
    }
}
