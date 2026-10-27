package com.hrm.controller;

import java.util.List;
import java.security.Principal;

import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.hrm.dto.PositionDTO;
import com.hrm.dto.PositionRequestDTO;
import com.hrm.service.PositionService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/positions")
public class PositionController {

    private final PositionService positionService;
    private final com.hrm.service.DepartmentHeadAccessService access;
    private final com.hrm.service.CatalogAuditService catalog;

    public PositionController(PositionService positionService,
            com.hrm.service.DepartmentHeadAccessService access, com.hrm.service.CatalogAuditService catalog) {
        this.positionService = positionService;
        this.access=access; this.catalog=catalog;
    }

    @GetMapping
public Page<PositionDTO> getAllPositions(
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "10") int size,
        @RequestParam(required = false) String keyword,
        @RequestParam(required = false) Boolean active, Principal principal) {

    access.requireManager(principal);
    return positionService.searchPositions(keyword, active, page, size);
}

    @GetMapping("/{id}")
    public PositionDTO getPositionById(@PathVariable Long id, Principal principal) {
        access.requireManager(principal);
        return positionService.getPositionById(id);
    }

    @PostMapping
    @org.springframework.web.bind.annotation.ResponseStatus(org.springframework.http.HttpStatus.CREATED)
    public PositionDTO addPosition(@Valid @RequestBody PositionRequestDTO requestDTO, Principal principal) {
        return catalog.createPosition(requestDTO, access.requireManager(principal));
    }

    @PutMapping("/{id}")
    public PositionDTO updatePosition(@PathVariable Long id, @Valid @RequestBody PositionRequestDTO requestDTO, Principal principal) {
        return catalog.updatePosition(id, requestDTO, access.requireManager(principal));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<String> deletePosition(@PathVariable Long id, Principal principal) {
        catalog.deactivatePosition(id, access.requireManager(principal));
        return ResponseEntity.ok("Da vo hieu hoa chuc vu thanh cong!");
    }
}
