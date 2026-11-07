package com.hrm.repository;

import com.hrm.model.SalaryComponent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface SalaryComponentRepository extends JpaRepository<SalaryComponent, Long> {

    Optional<SalaryComponent> findByComponentCode(String componentCode);

    boolean existsByComponentCode(String componentCode);
}