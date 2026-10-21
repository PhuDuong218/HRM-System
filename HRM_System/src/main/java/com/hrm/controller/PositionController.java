package com.hrm.controller;

import java.util.List;

import org.springframework.http.ResponseEntity;
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

    @GetMapping
    public List<PositionDTO> getAllPositions() {
        return positionService.getAllPositions();
    }

    @GetMapping("/{id}")
    public PositionDTO getPositionById(@PathVariable Long id) {
        return positionService.getPositionById(id);
    }

    @PostMapping
    public PositionDTO addPosition(@Valid @RequestBody PositionRequestDTO requestDTO) {
        return positionService.addPosition(requestDTO);
    }

    @PutMapping("/{id}")
    public PositionDTO updatePosition(@PathVariable Long id, @Valid @RequestBody PositionRequestDTO requestDTO) {
        System.out.println("ID: " + id);
        System.out.println("positionCode: " + requestDTO.getPositionCode());
        return positionService.updatePosition(id, requestDTO);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<String> deletePosition(@PathVariable Long id) {
        positionService.deletePosition(id);
        return ResponseEntity.ok("Da vo hieu hoa chuc vu thanh cong!");
    }
}
