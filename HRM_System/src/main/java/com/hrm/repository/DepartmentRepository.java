package com.hrm.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.hrm.model.Department;

@Repository
public interface DepartmentRepository extends JpaRepository<Department, Long> {
    boolean existsByDepartmentCode(String departmentCode);

    @org.springframework.data.jpa.repository.Query("""
            SELECT d FROM Department d
            WHERE (:keyword IS NULL OR LOWER(d.departmentCode) LIKE :keyword OR LOWER(d.departmentName) LIKE :keyword)
              AND (:active IS NULL OR d.isActive = :active)
            """)
    org.springframework.data.domain.Page<Department> search(
            @org.springframework.data.repository.query.Param("keyword") String keyword,
            @org.springframework.data.repository.query.Param("active") Boolean active,
            org.springframework.data.domain.Pageable pageable);

    @org.springframework.data.jpa.repository.Query("""
            SELECT d FROM Department d
            WHERE d.departmentId IN :scopeIds AND (:keyword IS NULL OR LOWER(d.departmentCode) LIKE :keyword OR LOWER(d.departmentName) LIKE :keyword)
              AND (:active IS NULL OR d.isActive = :active)
            """)
    org.springframework.data.domain.Page<Department> searchScoped(
            @org.springframework.data.repository.query.Param("keyword") String keyword,
            @org.springframework.data.repository.query.Param("active") Boolean active,
            @org.springframework.data.repository.query.Param("scopeIds") java.util.Collection<Long> scopeIds, org.springframework.data.domain.Pageable pageable);
}
