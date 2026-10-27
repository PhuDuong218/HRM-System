package com.hrm.repository;

import java.util.Collection;
import java.util.List;
import java.time.LocalDate;
import java.time.ZoneId;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.hrm.model.EmployeeAssignment;

public interface EmployeeAssignmentRepository
                extends JpaRepository<EmployeeAssignment, Long> {

        @Query("""
                        SELECT a FROM EmployeeAssignment a
                        JOIN FETCH a.employee JOIN FETCH a.department JOIN FETCH a.position
                        WHERE a.department.departmentId IN :departmentIds AND a.position.positionCode='TRUONG_PHONG'
                          AND a.effectiveFrom<=:asOf AND (a.effectiveTo IS NULL OR a.effectiveTo>:asOf)
                        """)
        List<EmployeeAssignment> findHeadsAt(@Param("departmentIds") Collection<Long> departmentIds,
                        @Param("asOf") java.time.LocalDate asOf);

        @Query("""
                        SELECT ea
                        FROM EmployeeAssignment ea
                        JOIN FETCH ea.employee
                        JOIN FETCH ea.department
                        JOIN FETCH ea.position
                        ORDER BY ea.employee.employeeId, ea.effectiveFrom DESC
                        """)
        List<EmployeeAssignment> findAllWithDetails();

        @Query("""
                        SELECT ea
                        FROM EmployeeAssignment ea
                        JOIN FETCH ea.employee
                        JOIN FETCH ea.department
                        JOIN FETCH ea.position
                        WHERE ea.employee.employeeId = :employeeId
                        ORDER BY ea.effectiveFrom DESC, ea.assignmentId DESC
                        """)
        List<EmployeeAssignment> findHistoryByEmployeeId(
                        @Param("employeeId") Long employeeId);

        @Query("""
                        SELECT ea
                        FROM EmployeeAssignment ea
                        JOIN FETCH ea.employee
                        JOIN FETCH ea.department
                        JOIN FETCH ea.position
                        WHERE ea.employee.employeeId = :employeeId
                        AND ea.effectiveFrom <= :asOf
                        AND (
                            ea.effectiveTo IS NULL
                            OR ea.effectiveTo > :asOf
                        )
                        """)
        List<EmployeeAssignment> findCurrentAssignments(
                        @Param("employeeId") Long employeeId,
                        @Param("asOf") LocalDate asOf);

        @Query("""
        SELECT a
        FROM EmployeeAssignment a
        JOIN FETCH a.department
        JOIN FETCH a.position
        WHERE a.employee.employeeId IN :employeeIds
        AND a.effectiveFrom <= :asOf
        AND (
            a.effectiveTo IS NULL
            OR a.effectiveTo > :asOf
        )
        """)
List<EmployeeAssignment> findCurrentByEmployeeIds(
        @Param("employeeIds") Collection<Long> employeeIds,
        @Param("asOf") LocalDate asOf);

        // Phân trang toàn bộ lịch sử phân công
        @Query(value = """
                        SELECT ea
                        FROM EmployeeAssignment ea
                        JOIN FETCH ea.employee
                        JOIN FETCH ea.department
                        JOIN FETCH ea.position
                        """, countQuery = """
                        SELECT COUNT(ea)
                        FROM EmployeeAssignment ea
                        """)
        Page<EmployeeAssignment> findAllPagedWithDetails(
                        Pageable pageable);

        // Phân trang lịch sử của một nhân viên
        @Query(value = """
                        SELECT ea
                        FROM EmployeeAssignment ea
                        JOIN FETCH ea.employee
                        JOIN FETCH ea.department
                        JOIN FETCH ea.position
                        WHERE ea.employee.employeeId = :employeeId
                        """, countQuery = """
                        SELECT COUNT(ea)
                        FROM EmployeeAssignment ea
                        WHERE ea.employee.employeeId = :employeeId
                        """)
        Page<EmployeeAssignment> findHistoryPagedByEmployeeId(
                        @Param("employeeId") Long employeeId,
                        Pageable pageable);

        default List<EmployeeAssignment> findCurrentAssignments(
        Long employeeId) {

    return findCurrentAssignments(
            employeeId,
            LocalDate.now(ZoneId.of("Asia/Ho_Chi_Minh")));
}

default List<EmployeeAssignment> findCurrentByEmployeeIds(
        Collection<Long> employeeIds) {

    return findCurrentByEmployeeIds(
            employeeIds,
            LocalDate.now(ZoneId.of("Asia/Ho_Chi_Minh")));
}
}
