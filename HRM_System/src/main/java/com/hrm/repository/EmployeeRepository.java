
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

    // Kiem tra ma nhan vien da ton tai hay chua
    boolean existsByEmployeeCode(String employeeCode);

    // Kiem tra nhan vien theo ID va trang thai lam viec
    boolean existsByEmployeeIdAndEmploymentStatus(
            Long employeeId,
            EmploymentStatus employmentStatus);

    // Lay tat ca nhan vien kem lich su phan cong
    @Query("""
            SELECT DISTINCT e FROM Employee e
            LEFT JOIN FETCH e.assignments a
            LEFT JOIN FETCH a.department
            LEFT JOIN FETCH a.position
            """)
    List<Employee> findAllWithAssignments();

    // Lay nhan vien theo ID kem lich su phan cong
    @Query("""
            SELECT DISTINCT e FROM Employee e
            LEFT JOIN FETCH e.assignments a
            LEFT JOIN FETCH a.department
            LEFT JOIN FETCH a.position
            WHERE e.employeeId = :employeeId
            """)
    Optional<Employee> findByIdWithAssignments(
            @Param("employeeId") Long employeeId);

    // Lay danh sach nhan vien kem phan cong chua co ngay ket thuc
    @Query("""
            SELECT DISTINCT e FROM Employee e
            LEFT JOIN FETCH e.assignments a
            LEFT JOIN FETCH a.department
            LEFT JOIN FETCH a.position
            WHERE a.effectiveTo IS NULL OR a IS NULL
            """)
    List<Employee> findAllWithCurrentAssignments();

    // Khoa ban ghi nhan vien khi cap nhat
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT e FROM Employee e WHERE e.employeeId = :id")
    Optional<Employee> findByIdForUpdate(@Param("id") Long id);

    // Tim kiem nhan vien co loc theo trang thai,
    // phong ban, chuc vu va thoi diem hieu luc
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

    @Query(value = """
            SELECT e
            FROM Employee e
            WHERE e.employeeId IN :scopeIds AND (
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
            WHERE e.employeeId IN :scopeIds AND (
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
    Page<Employee> searchEmployeesScoped(
            @Param("keyword") String keyword,
            @Param("status") EmploymentStatus status,
            @Param("departmentId") Long departmentId,
            @Param("positionId") Long positionId,
            @Param("asOf") LocalDate asOf,
            @org.springframework.data.repository.query.Param("scopeIds") java.util.Collection<Long> scopeIds, org.springframework.data.domain.Pageable pageable);

    // Ham tien ich: mac dinh tim kiem theo ngay hien tai tai Viet Nam
    default Page<Employee> searchEmployees(
            String keyword,
            EmploymentStatus status,
            Long departmentId,
            Long positionId,
            Pageable pageable) {

        LocalDate today = LocalDate.now(
                ZoneId.of("Asia/Ho_Chi_Minh"));

        return searchEmployees(
                keyword,
                status,
                departmentId,
                positionId,
                today,
                pageable);
    }
}
