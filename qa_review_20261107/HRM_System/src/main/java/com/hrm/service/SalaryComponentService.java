package com.hrm.service;

import com.hrm.dto.SalaryComponentDTO;
import com.hrm.dto.SalaryComponentRequestDTO;
import com.hrm.model.SalaryComponent;
import com.hrm.repository.SalaryComponentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class SalaryComponentService {

    private final SalaryComponentRepository repository;

    public SalaryComponentService(SalaryComponentRepository repository) {
        this.repository = repository;
    }

    // 1. Lấy tất cả danh mục
    public List<SalaryComponentDTO> getAllComponents() {
        return repository.findAll().stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }

    // 2. Lấy chi tiết theo ID
    public SalaryComponentDTO getComponentById(Long id) {
        SalaryComponent entity = repository.findById(id)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy thành phần lương với ID: " + id));
        return toDTO(entity);
    }

    // 3. Thêm mới khoản lương (kiểm tra UNIQUE mã khoản lương)
    @Transactional
    public SalaryComponentDTO createComponent(SalaryComponentRequestDTO request) {
        String code = request.getComponentCode().trim().toUpperCase();
        if (repository.existsByComponentCode(code)) {
            throw new RuntimeException("Mã thành phần lương đã tồn tại: " + code);
        }

        SalaryComponent entity = new SalaryComponent();
        entity.setComponentCode(code);
        entity.setComponentName(request.getComponentName().trim());
        entity.setDirection(request.getDirection());
        entity.setIsActive(request.getIsActive() != null ? request.getIsActive() : true);
        entity.setCreatedAt(LocalDateTime.now());

        SalaryComponent saved = repository.save(entity);
        return toDTO(saved);
    }

    // 4. Cập nhật thông tin
    @Transactional
    public SalaryComponentDTO updateComponent(Long id, SalaryComponentRequestDTO request) {
        SalaryComponent entity = repository.findById(id)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy thành phần lương với ID: " + id));

        // Nếu người dùng muốn đổi code, kiểm tra xem code mới có bị trùng với bản ghi khác không
        String newCode = request.getComponentCode().trim().toUpperCase();
        if (!entity.getComponentCode().equalsIgnoreCase(newCode) && repository.existsByComponentCode(newCode)) {
            throw new RuntimeException("Mã thành phần lương mới đã tồn tại: " + newCode);
        }

        entity.setComponentCode(newCode);
        entity.setComponentName(request.getComponentName().trim());
        entity.setDirection(request.getDirection());
        if (request.getIsActive() != null) {
            entity.setIsActive(request.getIsActive());
        }
        entity.setUpdatedAt(LocalDateTime.now());

        SalaryComponent updated = repository.save(entity);
        return toDTO(updated);
    }

    // 5. Vô hiệu hóa (Soft Delete để tránh trigger tr_salary_components_no_delete của SQL Server)
    @Transactional
    public void deactivateComponent(Long id) {
        SalaryComponent entity = repository.findById(id)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy thành phần lương với ID: " + id));
        
        entity.setIsActive(false);
        entity.setUpdatedAt(LocalDateTime.now());
        repository.save(entity);
    }

    private SalaryComponentDTO toDTO(SalaryComponent entity) {
        return new SalaryComponentDTO(
                entity.getComponentId(),
                entity.getComponentCode(),
                entity.getComponentName(),
                entity.getDirection(),
                entity.getIsActive()
        );
    }
}