package com.hrm.controller;

import java.util.List;
import com.hrm.dto.*;
import com.hrm.service.EmployeeAllowanceService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/employees/{employeeId}/allowances")
public class EmployeeAllowanceController {
    private final EmployeeAllowanceService service;

    public EmployeeAllowanceController(EmployeeAllowanceService service) {
        this.service = service;
    }

    @GetMapping
    public List<EmployeeAllowanceResponse> list(@PathVariable Long employeeId) {
        return service.list(employeeId);
    }

    @GetMapping("/{id}")
    public EmployeeAllowanceResponse get(@PathVariable Long employeeId, @PathVariable Long id) {
        return service.get(employeeId, id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public EmployeeAllowanceResponse create(@PathVariable Long employeeId,
            @Valid @RequestBody EmployeeAllowanceRequest r) {
        return service.create(employeeId, r);
    }

    @PutMapping("/{id}")
    public EmployeeAllowanceResponse update(@PathVariable Long employeeId, @PathVariable Long id,
            @Valid @RequestBody EmployeeAllowanceRequest r) {
        return service.updateFuture(employeeId, id, r);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long employeeId, @PathVariable Long id, @RequestParam String reason) {
        service.deleteFuture(employeeId, id, reason);
    }

    @PostMapping("/{id}/change-from")
    @ResponseStatus(HttpStatus.CREATED)
    public EmployeeAllowanceResponse change(@PathVariable Long employeeId, @PathVariable Long id,
            @Valid @RequestBody AllowanceChangeRequest r) {
        return service.changeFrom(employeeId, id, r);
    }

    @PostMapping("/{id}/end-from")
    public EmployeeAllowanceResponse end(@PathVariable Long employeeId, @PathVariable Long id,
            @Valid @RequestBody AllowanceEndRequest r) {
        return service.endFrom(employeeId, id, r);
    }
}