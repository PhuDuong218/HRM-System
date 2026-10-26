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

    @org.springframework.data.jpa.repository.Query("""
            SELECT p FROM Position p
            WHERE (:keyword IS NULL OR LOWER(p.positionCode) LIKE :keyword OR LOWER(p.positionName) LIKE :keyword)
              AND (:active IS NULL OR p.isActive = :active)
            """)
    org.springframework.data.domain.Page<Position> search(
            @org.springframework.data.repository.query.Param("keyword") String keyword,
            @org.springframework.data.repository.query.Param("active") Boolean active,
            org.springframework.data.domain.Pageable pageable);
}
