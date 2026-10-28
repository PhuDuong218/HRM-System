package com.hrm.controller;

import java.security.Principal;

import com.hrm.service.DepartmentHeadAccessService;
import com.hrm.service.EmployeeTerminationAuditService;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/employees")
public class EmployeeTerminationController {

    private final EmployeeTerminationAuditService terminationAudit;
    private final DepartmentHeadAccessService accessService;

    public EmployeeTerminationController(
            EmployeeTerminationAuditService terminationAudit,
            DepartmentHeadAccessService accessService) {

        this.terminationAudit = terminationAudit;
        this.accessService = accessService;
    }

    @PostMapping("/{id}/terminate")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void terminateEmployee(
            @PathVariable Long id,
            Principal principal) {

        Long actorAccountId = accessService.requireManager(principal);

        terminationAudit.terminate(id, actorAccountId);
    }
}