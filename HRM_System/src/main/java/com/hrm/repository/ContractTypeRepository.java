package com.hrm.repository;

import com.hrm.model.ContractType;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ContractTypeRepository extends JpaRepository<ContractType, Long> {
    boolean existsByTypeCode(String typeCode);
}