package com.hrm.controller;

import com.hrm.dto.*;
import com.hrm.enums.ContractStatus;
import com.hrm.service.ContractService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/contracts")
public class ContractController {
    private final ContractService service;
    public ContractController(ContractService service) { this.service = service; }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ContractResponse create(@Valid @RequestBody ContractCreateRequest request) {
        return service.create(request);
    }

    @GetMapping("/{id}")
    public ContractResponse getOne(@PathVariable Long id) {
        return service.getOne(id);
    }

    @GetMapping
    public PageResponse<ContractResponse> search(
            @RequestParam(required = false) Long employeeId,
            @RequestParam(required = false) ContractStatus status,
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        return service.search(employeeId, status, q, page, size);
    }

    @PostMapping("/{id}/renew")
    @ResponseStatus(HttpStatus.CREATED)
    public ContractResponse renew(@PathVariable Long id,
            @Valid @RequestBody ContractRenewRequest request) {
        return service.renew(id, request);
    }

    @PostMapping("/{id}/terminate")
    public ContractResponse terminate(@PathVariable Long id,
            @Valid @RequestBody ContractTerminateRequest request) {
        return service.terminate(id, request);
    }
}