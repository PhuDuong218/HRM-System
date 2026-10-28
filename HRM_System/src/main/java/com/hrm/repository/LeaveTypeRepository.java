package com.hrm.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.hrm.model.LeaveType;

@Repository
public interface LeaveTypeRepository
        extends JpaRepository<LeaveType, Long> {

    boolean existsByTypeCode(String typeCode);
}