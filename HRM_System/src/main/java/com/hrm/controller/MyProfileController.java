package com.hrm.controller;

import com.hrm.dto.EmployeeContactPatchDTO;
import com.hrm.dto.CurrentUserResponse;
import com.hrm.security.AuthenticatedAccount;
import com.hrm.service.AuthService;
import com.hrm.service.EmployeeContactService;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/me/profile")
public class MyProfileController {
    private final EmployeeContactService contacts;
    private final AuthService auth;
    private final com.hrm.service.EmployeeService employees;
    public MyProfileController(EmployeeContactService contacts, AuthService auth, com.hrm.service.EmployeeService employees) {
        this.contacts = contacts;
        this.auth = auth;
        this.employees = employees;
    }
    @GetMapping
    public com.hrm.dto.EmployeeDetailDTO get(@AuthenticationPrincipal AuthenticatedAccount actor) {
        auth.currentUser(actor);
        return employees.getEmployeeDetailById(actor.employeeId());
    }

    @PutMapping
    @Transactional
    public CurrentUserResponse update(@AuthenticationPrincipal AuthenticatedAccount actor,
                                      @Valid @RequestBody EmployeeContactPatchDTO request) {
        auth.currentUser(actor);
        contacts.updateOwnContact(actor, request);
        return auth.currentUser(actor);
    }
}
