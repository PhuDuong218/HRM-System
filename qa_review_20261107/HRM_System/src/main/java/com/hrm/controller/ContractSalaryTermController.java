package com.hrm.controller;

import java.time.LocalDate;
import java.util.List;
import com.hrm.dto.*;
import com.hrm.service.ContractSalaryTermService;
import com.hrm.service.SalaryRateService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.access.prepost.PreAuthorize;

@RestController
@PreAuthorize("hasAnyRole('ADMIN','HR')")
public class ContractSalaryTermController {
    private final ContractSalaryTermService terms;
    private final SalaryRateService rates;
    public ContractSalaryTermController(ContractSalaryTermService terms,SalaryRateService rates) {
        this.terms=terms; this.rates=rates;
    }

    @GetMapping("/contracts/{contractId}/salary-terms")
    public List<SalaryTermResponse> list(@PathVariable Long contractId) { return terms.list(contractId); }

    @GetMapping("/contracts/{contractId}/salary-terms/{id}")
    public SalaryTermResponse get(@PathVariable Long contractId,@PathVariable Long id) { return terms.get(contractId,id); }

    @PostMapping("/contracts/{contractId}/salary-terms")
    @ResponseStatus(HttpStatus.CREATED)
    public SalaryTermResponse create(@PathVariable Long contractId,@Valid @RequestBody SalaryTermRequest r) {
        return terms.create(contractId,r);
    }

    @PutMapping("/contracts/{contractId}/salary-terms/{id}")
    public SalaryTermResponse update(@PathVariable Long contractId,@PathVariable Long id,
            @Valid @RequestBody SalaryTermRequest r) { return terms.updateFuture(contractId,id,r); }

    @DeleteMapping("/contracts/{contractId}/salary-terms/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long contractId,@PathVariable Long id,
            @RequestParam @NotBlank @Size(max=500) String reason) { terms.deleteFuture(contractId,id,reason); }

    @PostMapping("/contracts/{contractId}/salary-terms/{id}/change-rate")
    @ResponseStatus(HttpStatus.CREATED)
    public SalaryTermResponse change(@PathVariable Long contractId,@PathVariable Long id,
            @Valid @RequestBody SalaryTermChangeRequest r) { return terms.changeRate(contractId,id,r); }

    @GetMapping("/employees/{employeeId}/salary-rates")
    public List<SalaryRateSegment> period(@PathVariable Long employeeId,
            @RequestParam @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate to) {
        return rates.getRatesForPeriod(employeeId,from,to);
    }
}