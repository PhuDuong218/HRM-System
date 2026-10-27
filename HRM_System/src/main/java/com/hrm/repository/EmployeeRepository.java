package com.hrm.repository;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.hrm.enums.EmploymentStatus;
import com.hrm.model.Employee;

import jakarta.persistence.LockModeType;

@Repository
public interface EmployeeRepository extends JpaRepository<Employee, Long> {

    boolean existsByEmployeeIdAndEmploymentStatus(Long employeeId, EmploymentStatus employmentStatus);

    boolean existsByEmployeeCode(String employeeCode);

    // Fetch full history so employees without a current assignment are not dropped.
    @Query("""
            select distinct e from Employee e
            left join fetch e.assignments a
            left join fetch a.department
            left join fetch a.position
            """)
    List<Employee> findAllWithAssignments();

    @Query("""
            select distinct e from Employee e
            left join fetch e.assignments a
            left join fetch a.department
            left join fetch a.position
            where e.employeeId = :employeeId
            """)
    Optional<Employee> findByIdWithAssignments(@Param("employeeId") Long employeeId);

    @Query("""
            SELECT DISTINCT e FROM Employee e
            LEFT JOIN FETCH e.assignments a
            LEFT JOIN FETCH a.department
            LEFT JOIN FETCH a.position
            WHERE a.effectiveTo IS NULL OR a IS NULL
            """)
    List<Employee> findAllWithCurrentAssignments();

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT e FROM Employee e WHERE e.employeeId = :id")
    Optional<Employee> findByIdForUpdate(@Param("id") Long id);

    @Query(value = """
            SELECT e
            FROM Employee e
            WHERE (
                LOWER(e.fullName) LIKE :keyword
                OR LOWER(e.employeeCode) LIKE :keyword
            )
            AND (:status IS NULL OR e.employmentStatus = :status)
            AND (
                (:departmentId IS NULL AND :positionId IS NULL)
                OR EXISTS (
                    SELECT a.assignmentId
                    FROM EmployeeAssignment a
                    WHERE a.employee = e
                    AND a.effectiveFrom <= :asOf
                    AND (a.effectiveTo IS NULL OR a.effectiveTo > :asOf)
                    AND (:departmentId IS NULL OR a.department.departmentId = :departmentId)
                    AND (:positionId IS NULL OR a.position.positionId = :positionId)
                )
            )
            """, countQuery = """
            SELECT COUNT(e)
            FROM Employee e
            WHERE (
                LOWER(e.fullName) LIKE :keyword
                OR LOWER(e.employeeCode) LIKE :keyword
            )
            AND (:status IS NULL OR e.employmentStatus = :status)
            AND (
                (:departmentId IS NULL AND :positionId IS NULL)
                OR EXISTS (
                    SELECT a.assignmentId
                    FROM EmployeeAssignment a
                    WHERE a.employee = e
                    AND a.effectiveFrom <= :asOf
                    AND (a.effectiveTo IS NULL OR a.effectiveTo > :asOf)
                    AND (:departmentId IS NULL OR a.department.departmentId = :departmentId)
                    AND (:positionId IS NULL OR a.position.positionId = :positionId)
                )
            )
            """)
    Page<Employee> searchEmployees(
            @Param("keyword") String keyword,
            @Param("status") EmploymentStatus status,
            @Param("departmentId") Long departmentId,
            @Param("positionId") Long positionId,
            @Param("asOf") LocalDate asOf,
            Pageable pageable);

    default Page<Employee> searchEmployees(
            String keyword,
            EmploymentStatus status,
            Long departmentId,
            Long positionId,
            Pageable pageable) {
        LocalDate today = LocalDate.now(ZoneId.of("Asia/Ho_Chi_Minh"));
        return searchEmployees(keyword, status, departmentId, positionId, today, pageable);
    }
}
