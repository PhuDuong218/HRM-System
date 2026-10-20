package com.hrm.controller;

import java.util.List;

import jakarta.validation.Valid;
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

import com.hrm.dto.QualificationLevelDTO;
import com.hrm.dto.QualificationLevelRequestDTO;
import com.hrm.service.QualificationLevelService;

@RestController
@RequestMapping("/qualification-levels")
public class QualificationLevelController {
    private final QualificationLevelService service;

    public QualificationLevelController(QualificationLevelService service) {
        this.service = service;
    }

    @GetMapping
    public List<QualificationLevelDTO> getAllQualificationLevels() { return service.getAllQualificationLevels(); }

    @GetMapping("/{id}")
    public QualificationLevelDTO getQualificationLevelById(@PathVariable Long id) { return service.getQualificationLevelById(id); }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public QualificationLevelDTO addQualificationLevel(@Valid @RequestBody QualificationLevelRequestDTO request) {
        return service.addQualificationLevel(request);
    }

    @PutMapping("/{id}")
    public QualificationLevelDTO updateQualificationLevel(@PathVariable Long id,
            @Valid @RequestBody QualificationLevelRequestDTO request) {
        return service.updateQualificationLevel(id, request);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteQualificationLevel(@PathVariable Long id) {
        service.deleteQualificationLevel(id);
        return ResponseEntity.noContent().build();
    }
}
