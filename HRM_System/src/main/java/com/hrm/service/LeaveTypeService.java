package com.hrm.service;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.hrm.dto.LeaveTypeDTO;
import com.hrm.dto.LeaveTypeRequestDTO;
import com.hrm.exception.ResourceNotFoundException;
import com.hrm.model.LeaveType;
import com.hrm.repository.LeaveTypeRepository;

@Service
public class LeaveTypeService {

    private final LeaveTypeRepository leaveTypeRepository;

    public LeaveTypeService(LeaveTypeRepository leaveTypeRepository) {
        this.leaveTypeRepository = leaveTypeRepository;
    }

    // 1. Lấy tất cả loại nghỉ
    @Transactional(readOnly = true)
    public List<LeaveTypeDTO> getAllLeaveTypes() {
        return leaveTypeRepository.findAll()
                .stream()
                .map(this::toDTO)
                .toList();
    }

    // 2. Lấy loại nghỉ theo ID
    @Transactional(readOnly = true)
    public LeaveTypeDTO getLeaveTypeById(Long id) {
        return toDTO(findRequired(id));
    }

    // 3. Thêm loại nghỉ
    @Transactional
    public LeaveTypeDTO addLeaveType(LeaveTypeRequestDTO request) {

        String code = request.getTypeCode().trim();

        if (leaveTypeRepository.existsByTypeCode(code)) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Mã loại nghỉ đã tồn tại: " + code
            );
        }

        LeaveType leaveType = new LeaveType();

        leaveType.setTypeCode(code);
        leaveType.setTypeName(request.getTypeName().trim());
        leaveType.setIsPaid(request.getIsPaid());

        leaveType.setIsActive(
                request.getIsActive() == null
                        ? true
                        : request.getIsActive()
        );

        leaveType.setCreatedAt(LocalDateTime.now());

        return toDTO(leaveTypeRepository.saveAndFlush(leaveType));
    }

    // 4. Cập nhật loại nghỉ
    @Transactional
    public LeaveTypeDTO updateLeaveType(
            Long id,
            LeaveTypeRequestDTO request) {

        LeaveType leaveType = findRequired(id);

        String code = request.getTypeCode().trim();

        // Không thay đổi mã sau khi đã tạo
        if (!leaveType.getTypeCode().equals(code)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Không được thay đổi mã loại nghỉ"
            );
        }

        // Bảo vệ ý nghĩa hưởng lương của dữ liệu lịch sử
        if (!leaveType.getIsPaid().equals(request.getIsPaid())) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Không được thay đổi chế độ hưởng lương của loại nghỉ đã tạo"
            );
        }

        leaveType.setTypeName(request.getTypeName().trim());

        if (request.getIsActive() != null) {
            leaveType.setIsActive(request.getIsActive());
        }

        leaveType.setUpdatedAt(LocalDateTime.now());

        return toDTO(leaveTypeRepository.saveAndFlush(leaveType));
    }

    // 5. Ngừng sử dụng loại nghỉ
    @Transactional
    public void deleteLeaveType(Long id) {

        LeaveType leaveType = findRequired(id);

        leaveType.setIsActive(false);
        leaveType.setUpdatedAt(LocalDateTime.now());

        leaveTypeRepository.saveAndFlush(leaveType);
    }

    // 6. Hàm dùng chung: tìm loại nghỉ
    private LeaveType findRequired(Long id) {

        return leaveTypeRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Không tìm thấy loại nghỉ: " + id
                ));
    }

    // 7. Chuyển Entity sang DTO
    private LeaveTypeDTO toDTO(LeaveType leaveType) {

        return new LeaveTypeDTO(
                leaveType.getLeaveTypeId(),
                leaveType.getTypeCode(),
                leaveType.getTypeName(),
                leaveType.getIsPaid(),
                leaveType.getIsActive()
        );
    }
}