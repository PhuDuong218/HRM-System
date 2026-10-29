package com.hrm.controller;

import java.util.List;
import com.hrm.dto.*;
import com.hrm.service.AllowanceTypeService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.access.prepost.PreAuthorize;

@RestController
@RequestMapping("/allowance-types")
@PreAuthorize("hasAnyRole('ADMIN','HR')")
public class AllowanceTypeController {
    private final AllowanceTypeService service;
    public AllowanceTypeController(AllowanceTypeService service) { this.service=service; }
    @GetMapping public List<AllowanceTypeResponse> list() { return service.list(); }
    @GetMapping("/{id}") public AllowanceTypeResponse get(@PathVariable Long id) { return service.get(id); }
    @PostMapping @ResponseStatus(HttpStatus.CREATED)
    public AllowanceTypeResponse create(@Valid @RequestBody AllowanceTypeRequest r) { return service.create(r); }
    @PutMapping("/{id}")
    public AllowanceTypeResponse update(@PathVariable Long id,@Valid @RequestBody AllowanceTypeRequest r) { return service.update(id,r); }
    @DeleteMapping("/{id}") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deactivate(@PathVariable Long id,@RequestParam String reason) { service.deactivate(id,reason); }
}