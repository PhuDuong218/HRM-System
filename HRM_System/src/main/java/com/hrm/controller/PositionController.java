
package com.hrm.controller;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.hrm.dto.PositionDTO;
import com.hrm.dto.PositionRequestDTO;
import com.hrm.service.PositionService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/positions")
public class PositionController {

    private final PositionService positionService;

    public PositionController(PositionService positionService) {
        this.positionService = positionService;
    }

    // 1. Xem danh sách chức vụ
    @GetMapping
    @PreAuthorize("hasAuthority('position.view')")
    public List<PositionDTO> getAllPositions() {
        return positionService.getAllPositions();
    }

    // 2. Xem chi tiết chức vụ theo ID
    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('position.view')")
    public PositionDTO getPositionById(@PathVariable Long id) {
        return positionService.getPositionById(id);
    }

    // 3. Thêm chức vụ mới
    @PostMapping
    @PreAuthorize("hasAuthority('position.create')")
    public PositionDTO addPosition(
            @Valid @RequestBody PositionRequestDTO requestDTO) {

        return positionService.addPosition(requestDTO);
    }

    // 4. Cập nhật chức vụ
    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('position.update')")
    public PositionDTO updatePosition(
            @PathVariable Long id,
            @Valid @RequestBody PositionRequestDTO requestDTO) {

        return positionService.updatePosition(id, requestDTO);
    }

    // 5. Vô hiệu hóa chức vụ
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('position.delete')")
    public ResponseEntity<String> deletePosition(@PathVariable Long id) {

        positionService.deletePosition(id);

        return ResponseEntity.ok(
                "Da vo hieu hoa chuc vu thanh cong!"
        );
    }
}
