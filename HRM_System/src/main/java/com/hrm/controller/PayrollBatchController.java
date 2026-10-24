package com.hrm.controller;

import com.hrm.dto.CreateDraftBatchRequestDTO;
import com.hrm.dto.PayrollBatchDTO;
import com.hrm.service.PayrollBatchService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/payroll-batches")
public class PayrollBatchController {

    private final PayrollBatchService service;

    public PayrollBatchController(PayrollBatchService service) {
        this.service = service;
    }

    @GetMapping
    public ResponseEntity<List<PayrollBatchDTO>> getAll() {
        return ResponseEntity.ok(service.getAllBatches());
    }

    @GetMapping("/period/{periodId}")
    public ResponseEntity<List<PayrollBatchDTO>> getByPeriod(@PathVariable Long periodId) {
        return ResponseEntity.ok(service.getBatchesByPeriod(periodId));
    }

    // P-05: API tạo bản nháp bảng lương
    @PostMapping("/draft")
    public ResponseEntity<PayrollBatchDTO> createDraft(@Valid @RequestBody CreateDraftBatchRequestDTO request) {
        PayrollBatchDTO created = service.createDraftBatch(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }
}