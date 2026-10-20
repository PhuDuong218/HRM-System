package com.hrm.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.hrm.model.QualificationLevel;

public interface QualificationLevelRepository extends JpaRepository<QualificationLevel, Long> {
    boolean existsByLevelCode(String levelCode);
}
