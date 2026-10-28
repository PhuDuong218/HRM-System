package com.hrm.controller;

import com.hrm.dto.SalaryComponentDTO;
import com.hrm.dto.SalaryComponentRequestDTO;
import com.hrm.service.SalaryComponentService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/salary-components") // Gọi thử: http://localhost:2005/salary-components
public class SalaryComponentController {

    private final SalaryComponentService service;

    public SalaryComponentController(SalaryComponentService service) {
        this.service = service;
    }

    @GetMapping
    public ResponseEntity<List<SalaryComponentDTO>> getAll() {
        return ResponseEntity.ok(service.getAllComponents());
    }

    @GetMapping("/{id}")
    public ResponseEntity<SalaryComponentDTO> getById(@PathVariable Long id) {
        return ResponseEntity.ok(service.getComponentById(id));
    }

    @PostMapping
    public ResponseEntity<SalaryComponentDTO> create(@Valid @RequestBody SalaryComponentRequestDTO request) {
        SalaryComponentDTO created = service.createComponent(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @PutMapping("/{id}")
    public ResponseEntity<SalaryComponentDTO> update(@PathVariable Long id, @Valid @RequestBody SalaryComponentRequestDTO request) {
        return ResponseEntity.ok(service.updateComponent(id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<String> delete(@PathVariable Long id) {
        service.deactivateComponent(id);
        return ResponseEntity.ok("Đã ngừng kích hoạt thành phần lương thành công!");
    }
}