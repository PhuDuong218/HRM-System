package com.hrm.service;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.hrm.dto.QualificationLevelDTO;
import com.hrm.dto.QualificationLevelRequestDTO;
import com.hrm.exception.ResourceNotFoundException;
import com.hrm.model.QualificationLevel;
import com.hrm.repository.QualificationLevelRepository;

@Service
public class QualificationLevelService {
    private final QualificationLevelRepository repository;

    public QualificationLevelService(QualificationLevelRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public List<QualificationLevelDTO> getAllQualificationLevels() {
        return repository.findAll().stream().map(this::toDTO).toList();
    }

    @Transactional(readOnly = true)
    public QualificationLevelDTO getQualificationLevelById(Long id) {
        return toDTO(getRequired(id));
    }

    @Transactional
    public QualificationLevelDTO addQualificationLevel(QualificationLevelRequestDTO request) {
        String code = request.getLevelCode().trim();
        if (repository.existsByLevelCode(code)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Ma trinh do da ton tai: " + code);
        }
        QualificationLevel level = new QualificationLevel();
        level.setLevelCode(code);
        level.setLevelName(request.getLevelName().trim());
        level.setDescription(request.getDescription());
        level.setIsActive(true);
        level.setCreatedAt(LocalDateTime.now());
        return toDTO(repository.saveAndFlush(level));
    }

    @Transactional
    public QualificationLevelDTO updateQualificationLevel(Long id, QualificationLevelRequestDTO request) {
        QualificationLevel level = getRequired(id);
        if (!level.getLevelCode().equals(request.getLevelCode().trim())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Khong duoc thay doi ma trinh do sau khi tao");
        }
        level.setLevelName(request.getLevelName().trim());
        level.setDescription(request.getDescription());
        if (request.getIsActive() != null) level.setIsActive(request.getIsActive());
        level.setUpdatedAt(LocalDateTime.now());
        return toDTO(repository.saveAndFlush(level));
    }

    @Transactional
    public void deleteQualificationLevel(Long id) {
        QualificationLevel level = getRequired(id);
        level.setIsActive(false);
        level.setUpdatedAt(LocalDateTime.now());
        repository.saveAndFlush(level);
    }

    private QualificationLevel getRequired(Long id) {
        return repository.findById(id).orElseThrow(() ->
                new ResourceNotFoundException("Khong tim thay cap trinh do: " + id));
    }

    private QualificationLevelDTO toDTO(QualificationLevel level) {
        return new QualificationLevelDTO(level.getQualificationLevelId(), level.getLevelCode(),
                level.getLevelName(), level.getDescription(), level.getIsActive());
    }
}
