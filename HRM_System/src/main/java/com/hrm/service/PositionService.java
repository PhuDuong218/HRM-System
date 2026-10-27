package com.hrm.service;

import java.time.LocalDateTime;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Locale;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.hrm.dto.PositionDTO;
import com.hrm.dto.PositionRequestDTO;
import com.hrm.exception.ResourceNotFoundException;
import com.hrm.model.Position;
import com.hrm.repository.PositionRepository;
import com.hrm.util.PagingUtils;

@Service
public class PositionService {

    private final PositionRepository positionRepository;
    private final AssignmentGuardService guard;
    private final Clock clock;

    public PositionService(PositionRepository positionRepository, AssignmentGuardService guard,
            @org.springframework.beans.factory.annotation.Qualifier("hrmBusinessClock") Clock clock) {
        this.positionRepository = positionRepository;
        this.guard=guard;
        this.clock=clock;
    }

    // Chuyen Entity sang DTO: khong tra cac truong audit / rowVersion.
    private PositionDTO convertToDTO(Position position) {
        PositionDTO dto = new PositionDTO();
        dto.setPositionId(position.getPositionId());
        dto.setPositionCode(position.getPositionCode());
        dto.setPositionName(position.getPositionName());
        dto.setDescription(position.getDescription());
        dto.setIsActive(position.getIsActive());
        return dto;
    }

    @Transactional(readOnly = true)
    public List<PositionDTO> getAllPositions() {
        return positionRepository.findAll().stream()
                .map(this::convertToDTO)
                .toList();
    }

    @Transactional(readOnly = true)
    public PositionDTO getPositionById(Long id) {
        Position position = positionRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Khong tim thay chuc vu co ID: " + id));
        return convertToDTO(position);
    }

    @Transactional
    public PositionDTO addPosition(PositionRequestDTO requestDTO) {
        String code = requestDTO.getPositionCode().trim();

        if (positionRepository.existsByPositionCode(code)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Ma chuc vu da ton tai: " + code);
        }

        Position position = new Position();
        position.setPositionCode(code);
        position.setPositionName(requestDTO.getPositionName().trim());
        position.setDescription(requestDTO.getDescription());
        position.setIsActive(true); // He thong tu gan trang thai khi tao moi
        position.setCreatedAt(LocalDateTime.now(clock.withZone(ZoneOffset.UTC)));

        return convertToDTO(positionRepository.save(position));
    }

    @Transactional
    public PositionDTO updatePosition(Long id, PositionRequestDTO requestDTO) {
        guard.lockTimeline();
        Position position = positionRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Khong tim thay chuc vu co ID: " + id));

        String code = requestDTO.getPositionCode().trim();
        // Theo thiet ke hien tai, ma chuc vu la co dinh, khong sua qua PUT.
        if (!position.getPositionCode().equals(code)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Khong duoc thay doi ma chuc vu khi cap nhat");
        }

        position.setPositionName(requestDTO.getPositionName().trim());
        position.setDescription(requestDTO.getDescription()); // null -> xoa mo ta

        if (requestDTO.getIsActive() != null) {
            if (AssignmentGuardService.HEAD_CODE.equals(position.getPositionCode()) && !requestDTO.getIsActive()) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,"Không ngừng sử dụng chức vụ hệ thống TRUONG_PHONG");
            }
            if (!requestDTO.getIsActive()) guard.requireNoPositionAssignments(id, LocalDate.now(clock));
            position.setIsActive(requestDTO.getIsActive());
        }

        position.setUpdatedAt(LocalDateTime.now(clock.withZone(ZoneOffset.UTC)));
        return convertToDTO(positionRepository.save(position));
    }

    @Transactional
    public void deletePosition(Long id) {
        guard.lockTimeline();
        Position position = positionRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Khong tim thay chuc vu co ID: " + id));

        // Xoa mem: khong xoa ban ghi khoi SQL Server.
        if (AssignmentGuardService.HEAD_CODE.equals(position.getPositionCode())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,"Không ngừng sử dụng chức vụ hệ thống TRUONG_PHONG");
        }
        guard.requireNoPositionAssignments(id, LocalDate.now(clock));
        position.setIsActive(false);
        position.setUpdatedAt(LocalDateTime.now(clock.withZone(ZoneOffset.UTC)));
        positionRepository.save(position);
    }

    @Transactional(readOnly = true)
public Page<PositionDTO> getPositionsPage(int page, int size) {
    return searchPositions(null, null, page, size);
}

@Transactional(readOnly = true)
public Page<PositionDTO> searchPositions(String keyword, Boolean active, int page, int size) {
    String pattern = keyword == null || keyword.isBlank() ? null : "%" + keyword.trim().toLowerCase(Locale.ROOT) + "%";
    return positionRepository.search(pattern, active,
            PagingUtils.create(
                    page,
                    size,
                    Sort.by(Sort.Direction.DESC, "positionId")
            )
    ).map(this::convertToDTO);
}
}
