package com.hrm.repository;

import java.time.LocalDate;
import com.hrm.enums.ContractStatus;
import com.hrm.model.Contract;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface ContractRepository extends JpaRepository<Contract, Long> {
    boolean existsByContractNumber(String contractNumber);

    @EntityGraph(attributePaths = {"employee", "contractType"})
    @Query("""
        select c from Contract c
        where (:employeeId is null or c.employee.employeeId = :employeeId)
          and (:status is null or c.status = :status)
          and lower(c.contractNumber) like :pattern escape '!'
        """)
    Page<Contract> search(
        @Param("employeeId") Long employeeId,
        @Param("status") ContractStatus status,
        @Param("pattern") String pattern,
        Pageable pageable);

    @Query("""
        select count(c) from Contract c
        where c.employee.employeeId = :employeeId
          and (c.endDate is null or c.endDate >= :startDate)
          and (:endDate is null or c.startDate <= :endDate)
        """)
    long countOverlapping(
        @Param("employeeId") Long employeeId,
        @Param("startDate") LocalDate startDate,
        @Param("endDate") LocalDate endDate);

    @Query(value = """
        select count_big(*) from dbo.contract_salary_terms
        where contract_id = :contractId
        """, nativeQuery = true)
    long countSalaryTerms(@Param("contractId") Long contractId);
}