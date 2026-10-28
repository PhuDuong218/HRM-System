
package com.hrm.controller;

import java.time.LocalDate;
import java.util.List;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import java.time.LocalDate;

import org.springframework.data.domain.Page;
import org.springframework.format.annotation.DateTimeFormat;
import com.hrm.dto.LeaveEntitlementDTO;
import com.hrm.dto.LeaveEntitlementRequestDTO;
import com.hrm.service.LeaveEntitlementService;

@RestController
@RequestMapping("/leave-entitlements")
public class LeaveEntitlementController {

    private final LeaveEntitlementService entitlementService;

    public LeaveEntitlementController(
            LeaveEntitlementService entitlementService) {

        this.entitlementService = entitlementService;
    }

    
// GET /leave-entitlements
@GetMapping
public Page<LeaveEntitlementDTO> getAllEntitlements(
        @RequestParam(required = false) Long employeeId,
        @RequestParam(required = false) Long leaveTypeId,

        @RequestParam(required = false)
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
        LocalDate fromDate,

        @RequestParam(required = false)
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
        LocalDate toDate,

        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "10") int size) {

    return entitlementService.getAllEntitlements(
            employeeId,
            leaveTypeId,
            fromDate,
            toDate,
            page,
            size
    );
}


    // 1. Lấy hạn mức theo nhân viên
    @GetMapping("/employee/{employeeId}")
    public List<LeaveEntitlementDTO> getByEmployeeId(
            @PathVariable Long employeeId) {

        return entitlementService.getByEmployeeId(employeeId);
    }

    // 2. Xem chi tiết một hạn mức
    @GetMapping("/{id}")
    public LeaveEntitlementDTO getById(@PathVariable Long id) {

        return entitlementService.getById(id);
    }

    // 3. Cấp hạn mức nghỉ
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public LeaveEntitlementDTO addLeaveEntitlement(
            @Valid @RequestBody LeaveEntitlementRequestDTO request) {

        return entitlementService.addLeaveEntitlement(request);
    }

    // 4. Cập nhật hạn mức nghỉ
    @PutMapping("/{id}")
    public LeaveEntitlementDTO updateLeaveEntitlement(
            @PathVariable Long id,
            @Valid @RequestBody LeaveEntitlementRequestDTO request) {

        return entitlementService.updateLeaveEntitlement(id, request);
    }
}