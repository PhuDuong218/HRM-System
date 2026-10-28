package com.hrm.repository;

import java.time.LocalDate;
import java.util.*;
import com.hrm.enums.*;
import com.hrm.model.EmployeeDecision;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface EmployeeDecisionRepository extends JpaRepository<EmployeeDecision,Long> {
    boolean existsByDecisionNumber(String number);
    @Query("select d.employee.employeeId from EmployeeDecision d where d.decisionId=:id")
    Optional<Long> findEmployeeIdById(@Param("id") Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select d from EmployeeDecision d where d.decisionId=:id")
    Optional<EmployeeDecision> findByIdForUpdate(@Param("id") Long id);

    @Query("""
        select d from EmployeeDecision d
        where (:employeeId is null or d.employee.employeeId=:employeeId)
          and (:status is null or d.approvalStatus=:status)
        """)
    Page<EmployeeDecision> search(@Param("employeeId") Long employeeId,
        @Param("status") DecisionApprovalStatus status, Pageable pageable);

    @Query("""
        select d from EmployeeDecision d
        where d.employee.employeeId=:employeeId and d.approvalStatus=:status
          and d.payrollEffect<>:noneEffect and d.applyDate between :fromDate and :toDate
        order by d.applyDate,d.decisionId
        """)
    List<EmployeeDecision> findPayrollCandidates(@Param("employeeId") Long employeeId,
        @Param("status") DecisionApprovalStatus status,
        @Param("noneEffect") DecisionPayrollEffect noneEffect,
        @Param("fromDate") LocalDate from, @Param("toDate") LocalDate to);
}