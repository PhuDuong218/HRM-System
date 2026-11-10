package com.hrm.repository;

import java.time.LocalDate;
import java.util.Optional;
import com.hrm.enums.ContractStatus;
import com.hrm.model.Contract;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface ContractRepository extends JpaRepository<Contract, Long> {
    @EntityGraph(attributePaths = "contractType")
    Page<Contract> findByEmployee_EmployeeId(Long employeeId, Pageable pageable);

    @EntityGraph(attributePaths = "contractType")
    Optional<Contract> findByContractIdAndEmployee_EmployeeId(Long contractId, Long employeeId);

    @EntityGraph(attributePaths={"contractType"})
    java.util.List<Contract> findByEmployee_EmployeeIdOrderByStartDateAscContractIdAsc(Long employeeId);
    boolean existsByContractNumber(String contractNumber);

    @EntityGraph(attributePaths = { "employee", "contractType" })
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

    @EntityGraph(attributePaths = { "employee", "contractType" })
    @Query("""
            select c from Contract c
            where (:employeeId is null or c.employee.employeeId = :employeeId)
              and (:status is null or c.status = :status)
              and lower(c.contractNumber) like :pattern escape '!'
              and c.employee.employeeId in :allowedEmployeeIds
            """)
    Page<Contract> searchScoped(@Param("employeeId") Long employeeId,
            @Param("status") ContractStatus status, @Param("pattern") String pattern,
            @Param("allowedEmployeeIds") java.util.List<Long> allowedEmployeeIds, Pageable pageable);

    @Query("""
            select count(c) from Contract c
            where c.employee.employeeId = :employeeId
              and c.status <> com.hrm.enums.ContractStatus.DA_HUY
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

    @Query("""
            select c from Contract c
            where c.employee.employeeId=:employeeId
              and c.status <> com.hrm.enums.ContractStatus.DA_HUY
              and c.startDate<=:toDate and (c.endDate is null or c.endDate>=:fromDate)
            """)
    java.util.List<Contract> findIntersecting(@Param("employeeId") Long employeeId,
            @Param("fromDate") LocalDate from, @Param("toDate") LocalDate to);

    @Query("""
            select c.contractId from Contract c
            where c.status=com.hrm.enums.ContractStatus.DA_KY
              and c.endDate<:today order by c.contractId
            """)
    java.util.List<Long> findExpiredIds(@Param("today") LocalDate today, Pageable pageable);
}
