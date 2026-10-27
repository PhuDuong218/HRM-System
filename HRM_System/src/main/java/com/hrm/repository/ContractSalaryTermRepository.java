package com.hrm.repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import com.hrm.model.ContractSalaryTerm;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface ContractSalaryTermRepository extends JpaRepository<ContractSalaryTerm,Long> {
    List<ContractSalaryTerm> findByContract_ContractIdOrderByEffectiveFromAsc(Long contractId);
    Optional<ContractSalaryTerm> findBySalaryTermIdAndContract_ContractId(Long id, Long contractId);

    @Query("""
        select count(t) from ContractSalaryTerm t
        where t.contract.contractId=:contractId
          and (:exceptId is null or t.salaryTermId<>:exceptId)
          and (t.effectiveTo is null or t.effectiveTo>=:fromDate)
          and (:toDate is null or t.effectiveFrom<=:toDate)
        """)
    long countOverlapping(@Param("contractId") Long contractId,
        @Param("fromDate") LocalDate from, @Param("toDate") LocalDate to,
        @Param("exceptId") Long exceptId);

    @EntityGraph(attributePaths={"contract", "contract.employee"})
    @Query("""
        select t from ContractSalaryTerm t
        where t.contract.employee.employeeId=:employeeId
          and t.effectiveFrom<=:toDate
          and (t.effectiveTo is null or t.effectiveTo>=:fromDate)
        """)
    List<ContractSalaryTerm> findIntersecting(@Param("employeeId") Long employeeId,
        @Param("fromDate") LocalDate from, @Param("toDate") LocalDate to);
}