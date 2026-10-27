
package com.hrm.controller;

import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import org.springframework.web.bind.annotation.*;

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

    // 1. Danh sách loại nghỉ có phân trang
    @GetMapping
    public Page<LeaveTypeDTO> getAllLeaveTypes(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {

        return leaveTypeService.getLeaveTypesPage(page, size);
    }

    // 2. Xem chi tiết loại nghỉ
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
    public ResponseEntity<Void> deleteLeaveType(
            @PathVariable Long id) {

        leaveTypeService.deleteLeaveType(id);

        return ResponseEntity.noContent().build();
    }
}
