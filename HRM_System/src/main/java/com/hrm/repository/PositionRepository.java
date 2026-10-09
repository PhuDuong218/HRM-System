package com.hrm.repository;

import com.hrm.model.Position;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface PositionRepository extends JpaRepository<Position, Long> {
    
    // Spring Boot sẽ tự động viết câu lệnh SQL dựa trên tên hàm này:
    // "SELECT * FROM positions WHERE position_code = ?"
    Position findByPositionCode(String positionCode);

    
    boolean existsByPositionCode(String positionCode);
}