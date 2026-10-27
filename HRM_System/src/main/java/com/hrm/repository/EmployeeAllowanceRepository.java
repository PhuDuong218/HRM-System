package com.hrm.repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import com.hrm.model.EmployeeAllowance;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface EmployeeAllowanceRepository extends JpaRepository<EmployeeAllowance, Long> {
    @EntityGraph(attributePaths = { "allowanceType" })
    List<EmployeeAllowance> findByEmployee_EmployeeIdOrderByEffectiveFromAsc(Long employeeId);

    Optional<EmployeeAllowance> findByEmployeeAllowanceIdAndEmployee_EmployeeId(Long id, Long employeeId);

    @Query("""
            select count(a) from EmployeeAllowance a
            where a.employee.employeeId=:employeeId and a.allowanceType.allowanceTypeId=:typeId
              and (:exceptId is null or a.employeeAllowanceId<>:exceptId)
              and (a.effectiveTo is null or a.effectiveTo>=:fromDate)
              and (:toDate is null or a.effectiveFrom<=:toDate)
            """)
    long countOverlapping(@Param("employeeId") Long employeeId, @Param("typeId") Long typeId,
            @Param("fromDate") LocalDate from, @Param("toDate") LocalDate to, @Param("exceptId") Long exceptId);

    @EntityGraph(attributePaths = { "allowanceType" })
    @Query("""
            select a from EmployeeAllowance a where a.employee.employeeId=:employeeId
              and a.effectiveFrom<=:toDate and (a.effectiveTo is null or a.effectiveTo>=:fromDate)
            order by a.allowanceType.allowanceTypeId,a.effectiveFrom,a.employeeAllowanceId
            """)
    List<EmployeeAllowance> findIntersecting(@Param("employeeId") Long employeeId,
            @Param("fromDate") LocalDate from, @Param("toDate") LocalDate to);
}