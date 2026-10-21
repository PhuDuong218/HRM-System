package com.hrm.service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import com.hrm.exception.ResourceNotFoundException;
import com.hrm.dto.DepartmentDTO;
import com.hrm.dto.DepartmentRequestDTO;
import com.hrm.model.Department;
import com.hrm.repository.DepartmentRepository;

@Service
public class DepartmentService {

    private final DepartmentRepository departmentRepository;

    public DepartmentService(DepartmentRepository departmentRepository) {
        this.departmentRepository = departmentRepository;
    }

// Hàm Helper dùng chung: Biến 1 Entity thành 1 DTO
    private DepartmentDTO convertToDTO(Department entity) {
        DepartmentDTO dto = new DepartmentDTO();
        dto.setDepartmentId(entity.getDepartmentId());
        dto.setDepartmentCode(entity.getDepartmentCode());
        dto.setDepartmentName(entity.getDepartmentName());
        dto.setDescription(entity.getDescription());
        dto.setIsActive(entity.getIsActive());
        return dto;
    }

    public DepartmentDTO getDepartment(Long id) {
        Department entity = departmentRepository.findById(id)
                .orElseThrow(() ->
    new ResourceNotFoundException("Không tìm thấy phòng ban có mã: " + id)
);
        return convertToDTO(entity);
    }

    // 2. Hàm lấy danh sách (Không truyền tham số)
    public List<DepartmentDTO> getAllDepartment() {
        List<Department> entities = departmentRepository.findAll();

        // Dùng Stream: Cho danh sách Entity chạy qua băng chuyền (stream)
        // map(): Giao cho cỗ máy convertToDTO xử lý từng phần tử
        // collect(): Đóng gói lại thành một Danh sách DTO mới
        return entities.stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }

    public DepartmentDTO addDepartment(DepartmentRequestDTO requestDTO) {
        // Tự tay tạo một thực thể gốc mới tinh, hoàn toàn không dính líu đến rác của Client
        Department newDepartment = new Department();
        newDepartment.setDepartmentCode(requestDTO.getDepartmentCode());
        newDepartment.setDepartmentName(requestDTO.getDepartmentName());
        newDepartment.setDescription(requestDTO.getDescription());

        // Nhồi thông tin hệ thống (Bếp trưởng hoàn toàn làm chủ)
        newDepartment.setCreatedAt(LocalDateTime.now());
        newDepartment.setIsActive(true);

        // Lưu xuống Database
        Department savedDepartment = departmentRepository.save(newDepartment);

        // Đưa qua hàm Helper để dọn dẹp trước khi bưng ra cho khách
        return convertToDTO(savedDepartment);
    }

    // 2. Hàm Cập nhật
    public DepartmentDTO updateDepartment(Long id, DepartmentRequestDTO requestDTO) {
        Department existingDepartment = departmentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy phòng ban có mã: " + id));

        // Chỉ chép những gì khách gửi lên (Mã phòng ban là duy nhất, không cho sửa)
        if (requestDTO.getDepartmentName() != null) {
            existingDepartment.setDepartmentName(requestDTO.getDepartmentName());
        }
        if (requestDTO.getDescription() == null) {
        } else {
            existingDepartment.setDescription(requestDTO.getDescription());
        }
        if (requestDTO.getIsActive() != null) {
            existingDepartment.setIsActive(requestDTO.getIsActive());
        }

        existingDepartment.setUpdatedAt(LocalDateTime.now());

        Department updatedDepartment = departmentRepository.save(existingDepartment);
        return convertToDTO(updatedDepartment);
    }

    public void deleteDepartment(Long id) {
        Department existingDepartment = departmentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy phòng ban có mã: " + id));

        // Xóa mềm: Tắt trạng thái hoạt động và lưu lại//
        existingDepartment.setIsActive(false);
        existingDepartment.setUpdatedAt(LocalDateTime.now());
        departmentRepository.save(existingDepartment);
    }
}
