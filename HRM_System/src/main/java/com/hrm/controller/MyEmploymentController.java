package com.hrm.controller;

import com.hrm.dto.*;
import com.hrm.security.AuthenticatedAccount;
import com.hrm.service.MyEmploymentService;
import java.util.List;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/me")
public class MyEmploymentController {
    private final MyEmploymentService service;

    public MyEmploymentController(MyEmploymentService service) {
        this.service = service;
    }

    @GetMapping("/contracts")
    public PageResponse<MyContractResponse> contracts(@AuthenticationPrincipal AuthenticatedAccount actor,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "10") int size) {
        return service.contracts(actor, page, size);
    }

    @GetMapping("/contracts/{id}")
    public MyContractDetailResponse contractDetail(@AuthenticationPrincipal AuthenticatedAccount actor,
            @PathVariable Long id) {
        return service.contractDetail(actor, id);
    }

    @GetMapping("/allowances")
    public List<MyAllowanceResponse> allowances(@AuthenticationPrincipal AuthenticatedAccount actor) {
        return service.allowances(actor);
    }

    @GetMapping("/decisions")
    public PageResponse<MyDecisionResponse> decisions(@AuthenticationPrincipal AuthenticatedAccount actor,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "10") int size) {
        return service.decisions(actor, page, size);
    }
}
