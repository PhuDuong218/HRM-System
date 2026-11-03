package com.hrm.controller;

import java.security.Principal;

import com.hrm.service.DepartmentHeadAccessService;
import com.hrm.service.EmployeeTerminationAuditService;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/employees")
public class EmployeeTerminationController {
    @org.springframework.beans.factory.annotation.Autowired private com.hrm.service.EmployeeRequestService requests;

    private final EmployeeTerminationAuditService terminationAudit;
    private final DepartmentHeadAccessService accessService;

    public EmployeeTerminationController(
            EmployeeTerminationAuditService terminationAudit,
            DepartmentHeadAccessService accessService) {

        this.terminationAudit = terminationAudit;
        this.accessService = accessService;
    }

    /** Retained for old clients; new UI submits an explicit decision to termination-decisions. */
    @Deprecated @PostMapping("/{id}/terminate") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void terminateEmployee(@PathVariable Long id,Principal principal){terminationAudit.terminate(id,accessService.requireManager(principal));}
    @PostMapping("/{id}/termination-decisions")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void terminateEmployee(
            @PathVariable Long id,
            @jakarta.validation.Valid @RequestBody com.hrm.dto.DirectTerminationRequest request,
            Principal principal) {

        Long actorAccountId = accessService.requireManager(principal);

        requests.directTermination(id,request,principal);
    }
}