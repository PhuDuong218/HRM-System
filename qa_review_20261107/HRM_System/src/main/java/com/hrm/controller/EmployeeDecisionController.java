package com.hrm.controller;

import com.hrm.dto.*;
import com.hrm.enums.DecisionApprovalStatus;
import com.hrm.service.*;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.access.prepost.PreAuthorize;

@RestController
@RequestMapping("/employee-decisions")
@PreAuthorize("hasAnyRole('ADMIN','HR')")
public class EmployeeDecisionController {
    private final EmployeeDecisionService service;

    public EmployeeDecisionController(EmployeeDecisionService service) {
        this.service = service;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public DecisionResponse create(@Valid @RequestBody DecisionRequest r) {
        return service.create(r);
    }

    @GetMapping("/{id}")
    public DecisionResponse get(@PathVariable Long id) {
        return service.get(id);
    }

    @GetMapping
    public PageResponse<DecisionResponse> list(@RequestParam(required = false) Long employeeId,
            @RequestParam(required = false) DecisionApprovalStatus status,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "10") int size) {
        return service.list(employeeId, status, page, size);
    }

    @PutMapping("/{id}")
    public DecisionResponse update(@PathVariable Long id, @Valid @RequestBody DecisionRequest r) {
        return service.update(id, r);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id, @RequestParam String reason) {
        service.delete(id, reason);
    }

    @PostMapping("/{id}/approve")
    public DecisionResponse approve(@PathVariable Long id, @Valid @RequestBody DecisionActionRequest r) {
        return service.approve(id, r);
    }

    @PostMapping("/{id}/reject")
    public DecisionResponse reject(@PathVariable Long id, @Valid @RequestBody DecisionActionRequest r) {
        return service.reject(id, r);
    }
}