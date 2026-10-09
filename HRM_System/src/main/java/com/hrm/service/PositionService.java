package com.hrm.service;
import java.time.LocalDateTime;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.hrm.model.Position;
import com.hrm.repository.PositionRepository;

@Service
public class PositionService {

    @Autowired
    private PositionRepository positionRepository;

    // 1. Hàm lấy danh sách chức vụ
    public List<Position> getAllPositions() {
        return positionRepository.findAll();
    }

    public Position getPositionById(Long id) {
        return positionRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy chức vụ có mã: " + id));
    }

    // 2. Hàm thêm mới (Đã vá lỗi 515 và chặn trùng lặp)
    public Position addPosition(Position position) {
        // KIỂM TRA NGHIỆP VỤ: Tài liệu quy định mã chức vụ (position_code) là UNIQUE (duy nhất).
        // Nếu mã này đã có trong Database, lập tức chặn lại và ném lỗi, không cho lưu.
        if (positionRepository.existsByPositionCode(position.getPositionCode())) {
            throw new RuntimeException("Mã chức vụ đã tồn tại: " + position.getPositionCode());
        }

        // TỰ ĐỘNG GÁN GIÁ TRỊ: Tránh lỗi 515 Cannot insert NULL của SQL Server.
        position.setCreatedAt(LocalDateTime.now());
        position.setIsActive(true); // Trạng thái mặc định là 1 (đang sử dụng)[cite: 4].

        return positionRepository.save(position);
    }

    // 3. Hàm cập nhật (Vá lỗi ghi đè dữ liệu và lỗi Hibernate tự Insert)
    public Position updatePosition(Long id, Position updatedPosition) {
        Position existingPosition = positionRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy chức vụ có mã: " + id));

        // CHỈ COPY NHỮNG TRƯỜNG ĐƯỢC GỬI LÊN (Khác null)
        if (updatedPosition.getPositionName() != null) {
            existingPosition.setPositionName(updatedPosition.getPositionName());
        }

        if (updatedPosition.getDescription() != null) {
            existingPosition.setDescription(updatedPosition.getDescription());
        }

        if (updatedPosition.getIsActive() != null) {
            existingPosition.setIsActive(updatedPosition.getIsActive());
        }

        // ĐÓNG DẤU THỜI GIAN
        existingPosition.setUpdatedAt(LocalDateTime.now());

        return positionRepository.save(existingPosition);
    }

    public void deletePosition(Long id) {
        Position position = getPositionById(id);

        // Cập nhật trạng thái thành "Ngừng sử dụng" (0)
        position.setIsActive(false);
        position.setUpdatedAt(LocalDateTime.now());

        positionRepository.save(position);
    }
}
