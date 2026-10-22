package com.hrm.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.hrm.dto.LeaveTypeDTO;
import com.hrm.dto.LeaveTypeRequestDTO;
import com.hrm.service.LeaveTypeService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/leave-types")
public class LeaveTypeController {

    private final LeaveTypeService leaveTypeService;

    public LeaveTypeController(LeaveTypeService leaveTypeService) {
        this.leaveTypeService = leaveTypeService;
    }

    // 1. Xem tất cả loại nghỉ
    @GetMapping
    public List<LeaveTypeDTO> getAllLeaveTypes() {
        return leaveTypeService.getAllLeaveTypes();
    }

    // 2. Xem loại nghỉ theo ID
    @GetMapping("/{id}")
    public LeaveTypeDTO getLeaveTypeById(@PathVariable Long id) {
        return leaveTypeService.getLeaveTypeById(id);
    }

    // 3. Thêm loại nghỉ
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public LeaveTypeDTO addLeaveType(
            @Valid @RequestBody LeaveTypeRequestDTO request) {

        return leaveTypeService.addLeaveType(request);
    }

    // 4. Cập nhật loại nghỉ
    @PutMapping("/{id}")
    public LeaveTypeDTO updateLeaveType(
            @PathVariable Long id,
            @Valid @RequestBody LeaveTypeRequestDTO request) {

        return leaveTypeService.updateLeaveType(id, request);
    }

    // 5. Ngừng sử dụng loại nghỉ
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteLeaveType(@PathVariable Long id) {

        leaveTypeService.deleteLeaveType(id);

        return ResponseEntity.noContent().build();
    }
}