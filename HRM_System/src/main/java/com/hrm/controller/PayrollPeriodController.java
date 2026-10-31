package com.hrm.controller;

import com.hrm.dto.PayrollPeriodDTO;
import com.hrm.dto.PayrollPeriodRequestDTO;
import com.hrm.service.PayrollPeriodService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/payroll-periods")
@PreAuthorize("hasAnyRole('ADMIN', 'HR') and hasAuthority('PAYROLL_MANAGE')")
public class PayrollPeriodController {

    private final PayrollPeriodService service;

    public PayrollPeriodController(PayrollPeriodService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'HR') and hasAnyAuthority('PAYROLL_VIEW', 'PAYROLL_MANAGE')")
    public ResponseEntity<List<PayrollPeriodDTO>> getAll() {
        return ResponseEntity.ok(service.getAllPeriods());
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'HR') and hasAnyAuthority('PAYROLL_VIEW', 'PAYROLL_MANAGE')")
    public ResponseEntity<PayrollPeriodDTO> getById(@PathVariable Long id) {
        return ResponseEntity.ok(service.getPeriodById(id));
    }

    @PostMapping
    public ResponseEntity<PayrollPeriodDTO> create(@Valid @RequestBody PayrollPeriodRequestDTO request) {
        PayrollPeriodDTO created = service.createPeriod(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @PutMapping("/{id}")
    public ResponseEntity<PayrollPeriodDTO> update(@PathVariable Long id, @Valid @RequestBody PayrollPeriodRequestDTO request) {
        return ResponseEntity.ok(service.updatePeriod(id, request));
    }
}
