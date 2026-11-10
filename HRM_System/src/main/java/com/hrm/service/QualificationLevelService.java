package com.hrm.service;

import java.time.LocalDateTime;
import java.util.List;


import org.springframework.data.domain.Page;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.transaction.annotation.Transactional;
import com.hrm.dto.QualificationLevelDTO;
import com.hrm.dto.QualificationLevelRequestDTO;
import com.hrm.exception.ResourceNotFoundException;
import com.hrm.model.QualificationLevel;
import com.hrm.repository.QualificationLevelRepository;
import com.hrm.util.PagingUtils;

@Service
public class QualificationLevelService {
    @org.springframework.beans.factory.annotation.Autowired private QualificationMetadata metadata;
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
        level.setCreatedAt(LocalDateTime.now(java.time.ZoneOffset.UTC));
        repository.saveAndFlush(level);
        if(metadata!=null)metadata.rank(level.getQualificationLevelId(),request.getRankOrder());
        return toDTO(level);
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
        level.setUpdatedAt(LocalDateTime.now(java.time.ZoneOffset.UTC));
        repository.saveAndFlush(level);
        if(metadata!=null)metadata.rank(level.getQualificationLevelId(),request.getRankOrder());
        return toDTO(level);
    }

    @Transactional
    public void deleteQualificationLevel(Long id) {
        QualificationLevel level = getRequired(id);
        level.setIsActive(false);
        level.setUpdatedAt(LocalDateTime.now(java.time.ZoneOffset.UTC));
        repository.saveAndFlush(level);
    }

    private QualificationLevel getRequired(Long id) {
        return repository.findById(id).orElseThrow(() ->
                new ResourceNotFoundException("Khong tim thay cap trinh do: " + id));
    }

    private QualificationLevelDTO toDTO(QualificationLevel level) {
        return new QualificationLevelDTO(level.getQualificationLevelId(), level.getLevelCode(),
                level.getLevelName(), level.getDescription(), level.getIsActive(),metadata==null?null:metadata.rank(level.getQualificationLevelId()));
    }

    @Transactional(readOnly = true)
    public Page<QualificationLevelDTO> getLevelsPage(
        int page, int size) {

    return repository.findAll(
            PagingUtils.create(
                    page,
                    size,
                    Sort.by(
                            Sort.Direction.DESC,
                            "qualificationLevelId"
                    )
            )
    ).map(this::toDTO);
}

    @Transactional(readOnly = true)
    public Page<QualificationLevelDTO> searchLevels(String keyword, Boolean active, int page, int size) {
        if ((keyword == null || keyword.isBlank()) && active == null) return getLevelsPage(page, size);
        PagingUtils.create(page, size, Sort.unsorted());
        var rows = repository.findAll(Sort.by(Sort.Direction.DESC, "qualificationLevelId"))
                .stream().filter(row -> active == null || active.equals(row.getIsActive()))
                .filter(row -> com.hrm.util.PersonnelSearch.matches(keyword, row.getLevelCode(), row.getLevelName()))
                .map(this::toDTO).toList();
        return com.hrm.util.PersonnelSearch.page(rows, page, size);
    }
}
