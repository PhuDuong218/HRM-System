package com.hrm.repository;

import com.hrm.model.PayrollPeriod;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface PayrollPeriodRepository extends JpaRepository<PayrollPeriod, Long> {

    Optional<PayrollPeriod> findByPeriodCode(String periodCode);

    boolean existsByPeriodCode(String periodCode);
}