package com.hrm.controller;

import java.security.Principal;

import com.hrm.dto.EmployeeDetailDTO;
import com.hrm.service.DepartmentHeadAccessService;
import com.hrm.service.EmployeeService;
import com.hrm.dto.EmployeeContactPatchDTO;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.hrm.service.EmployeeContactService;
@RestController
@RequestMapping("/employees")
public class EmployeeDetailController {

    private final EmployeeService employeeService;
    private final DepartmentHeadAccessService accessService;
private final EmployeeContactService contactService;
    public EmployeeDetailController(
        EmployeeService employeeService,
        DepartmentHeadAccessService accessService,
        EmployeeContactService contactService) {

    this.employeeService = employeeService;
    this.accessService = accessService;
    this.contactService = contactService;
}

    @GetMapping("/{id}/detail")
    public EmployeeDetailDTO getEmployeeDetail(
            @PathVariable Long id,
            Principal principal) {

        var actor=com.hrm.service.PersonnelScopeService.current();
        if(actor==null || !"EMPLOYEE".equals(actor.roleCode()) || !java.util.Objects.equals(actor.employeeId(),id)) accessService.requireManager(principal);

        return employeeService.getEmployeeDetailById(id);
    }

    @PatchMapping("/{id}/contact")
public EmployeeDetailDTO patchEmployeeContact(
        @PathVariable Long id,
        @Valid @RequestBody EmployeeContactPatchDTO request,
        Principal principal) {

    return contactService.patchContact(id, request, principal);
}


}