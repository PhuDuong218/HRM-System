package com.hrm.service;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.hrm.dto.PositionDTO;
import com.hrm.dto.PositionRequestDTO;
import com.hrm.exception.ResourceNotFoundException;
import com.hrm.model.Position;
import com.hrm.repository.PositionRepository;

@Service
public class PositionService {

    private final PositionRepository positionRepository;

    public PositionService(PositionRepository positionRepository) {
        this.positionRepository = positionRepository;
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
        position.setCreatedAt(LocalDateTime.now());

        return convertToDTO(positionRepository.save(position));
    }

    @Transactional
    public PositionDTO updatePosition(Long id, PositionRequestDTO requestDTO) {
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
            position.setIsActive(requestDTO.getIsActive());
        }

        position.setUpdatedAt(LocalDateTime.now());
        return convertToDTO(positionRepository.save(position));
    }

    @Transactional
    public void deletePosition(Long id) {
        Position position = positionRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Khong tim thay chuc vu co ID: " + id));

        // Xoa mem: khong xoa ban ghi khoi SQL Server.
        position.setIsActive(false);
        position.setUpdatedAt(LocalDateTime.now());
        positionRepository.save(position);
    }
}
