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
    public MyProfileController(EmployeeContactService contacts, AuthService auth) {
        this.contacts = contacts;
        this.auth = auth;
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
