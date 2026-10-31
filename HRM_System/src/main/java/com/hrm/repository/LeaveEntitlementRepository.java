
package com.hrm.repository;

import java.time.LocalDate;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.hrm.model.LeaveEntitlement;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
public interface LeaveEntitlementRepository
        extends JpaRepository<LeaveEntitlement, Long> {

    @Query("""
        SELECT le
        FROM LeaveEntitlement le
        JOIN FETCH le.employee
        JOIN FETCH le.leaveType
        WHERE le.employee.employeeId = :employeeId
        ORDER BY le.periodStart DESC
        """)
    List<LeaveEntitlement> findByEmployeeId(
            @Param("employeeId") Long employeeId);

    @Query("""
        SELECT CASE WHEN COUNT(le) > 0
                    THEN true ELSE false END
        FROM LeaveEntitlement le
        WHERE le.employee.employeeId = :employeeId
        AND le.leaveType.leaveTypeId = :leaveTypeId
        AND le.periodStart < :periodEnd
        AND le.periodEnd > :periodStart
        """)
    boolean existsOverlappingPeriod(
            @Param("employeeId") Long employeeId,
            @Param("leaveTypeId") Long leaveTypeId,
            @Param("periodStart") LocalDate periodStart,
            @Param("periodEnd") LocalDate periodEnd);

    
@Query(
    value = """
        SELECT le
        FROM LeaveEntitlement le
        JOIN FETCH le.employee
        JOIN FETCH le.leaveType
        WHERE (:employeeId IS NULL
               OR le.employee.employeeId = :employeeId)
          AND (:leaveTypeId IS NULL
               OR le.leaveType.leaveTypeId = :leaveTypeId)
          AND (:fromDate IS NULL
               OR le.periodEnd > :fromDate)
          AND (:toDate IS NULL
               OR le.periodStart < :toDate)
        """,
    countQuery = """
        SELECT COUNT(le)
        FROM LeaveEntitlement le
        WHERE (:employeeId IS NULL
               OR le.employee.employeeId = :employeeId)
          AND (:leaveTypeId IS NULL
               OR le.leaveType.leaveTypeId = :leaveTypeId)
          AND (:fromDate IS NULL
               OR le.periodEnd > :fromDate)
          AND (:toDate IS NULL
               OR le.periodStart < :toDate)
        """
)
Page<LeaveEntitlement> searchEntitlements(
        @Param("employeeId") Long employeeId,
        @Param("leaveTypeId") Long leaveTypeId,
        @Param("fromDate") LocalDate fromDate,
        @Param("toDate") LocalDate toDate,
        Pageable pageable
);

    @Query(
    value = """
        SELECT le
        FROM LeaveEntitlement le
        JOIN FETCH le.employee
        JOIN FETCH le.leaveType
        WHERE le.employee.employeeId IN :scopeIds AND (:employeeId IS NULL
               OR le.employee.employeeId = :employeeId)
          AND (:leaveTypeId IS NULL
               OR le.leaveType.leaveTypeId = :leaveTypeId)
          AND (:fromDate IS NULL
               OR le.periodEnd > :fromDate)
          AND (:toDate IS NULL
               OR le.periodStart < :toDate)
        """,
    countQuery = """
        SELECT COUNT(le)
        FROM LeaveEntitlement le
        WHERE le.employee.employeeId IN :scopeIds AND (:employeeId IS NULL
               OR le.employee.employeeId = :employeeId)
          AND (:leaveTypeId IS NULL
               OR le.leaveType.leaveTypeId = :leaveTypeId)
          AND (:fromDate IS NULL
               OR le.periodEnd > :fromDate)
          AND (:toDate IS NULL
               OR le.periodStart < :toDate)
        """
)
Page<LeaveEntitlement> searchEntitlementsScoped(
        @Param("employeeId") Long employeeId,
        @Param("leaveTypeId") Long leaveTypeId,
        @Param("fromDate") LocalDate fromDate,
        @Param("toDate") LocalDate toDate,
        @org.springframework.data.repository.query.Param("scopeIds") java.util.Collection<Long> scopeIds, org.springframework.data.domain.Pageable pageable
);
        
}
