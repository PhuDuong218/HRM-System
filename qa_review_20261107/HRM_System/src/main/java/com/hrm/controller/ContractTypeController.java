package com.hrm.controller;

import java.util.List;
import com.hrm.dto.ContractTypeRequest;
import com.hrm.dto.ContractTypeResponse;
import com.hrm.service.ContractTypeService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.access.prepost.PreAuthorize;

@RestController
@RequestMapping("/contract-types")
@PreAuthorize("hasAnyRole('ADMIN','HR')")
public class ContractTypeController {
    private final ContractTypeService service;
    public ContractTypeController(ContractTypeService service) { this.service = service; }

    @GetMapping
    public List<ContractTypeResponse> getAll() { return service.getAll(); }

    @GetMapping("/{id}")
    public ContractTypeResponse getOne(@PathVariable Long id) { return service.getOne(id); }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ContractTypeResponse create(@Valid @RequestBody ContractTypeRequest request) {
        return service.create(request);
    }

    @PutMapping("/{id}")
    public ContractTypeResponse update(@PathVariable Long id,
            @Valid @RequestBody ContractTypeRequest request) {
        return service.update(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deactivate(@PathVariable Long id) { service.deactivate(id); }
}